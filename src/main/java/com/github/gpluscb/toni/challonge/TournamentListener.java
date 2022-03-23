package com.github.gpluscb.toni.challonge;

import at.stefangeyer.challonge.model.Match;
import at.stefangeyer.challonge.model.Participant;
import at.stefangeyer.challonge.model.Tournament;
import at.stefangeyer.challonge.model.enumeration.MatchState;
import at.stefangeyer.challonge.model.enumeration.TournamentState;
import at.stefangeyer.challonge.model.enumeration.TournamentType;
import com.github.gpluscb.challonge_listener.events.tournament.GenericTournamentEvent;
import com.github.gpluscb.challonge_listener.events.tournament.TournamentCompletedAtChangedEvent;
import com.github.gpluscb.challonge_listener.events.tournament.TournamentDeletedEvent;
import com.github.gpluscb.challonge_listener.events.tournament.TournamentStateChangedEvent;
import com.github.gpluscb.challonge_listener.events.tournament.match.MatchStateChangedEvent;
import com.github.gpluscb.challonge_listener.listener.ListenerAdapter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// TODO: More logging
// TODO: Linking between participants/discord members
// TODO: Embeds?
// TODO: Maybe only trust DB, don't keep cache locally. Seems like a recipe for illegal program state. Wait no we have to at least in some sense because of how ChallongeListener works... But we can try and limit it to that
// TODO: THIS IS ON HOLD FOR NOW! Will need to fix the other project first, might not be worth.
public class TournamentListener extends ListenerAdapter {
    private final static Logger log = LogManager.getLogger(TournamentListener.class);

    @Nonnull
    private final ShardManager jda;
    @Nonnull
    private final ScheduledExecutorService scheduler;

    @Nonnull
    private final List<Subscription> subscriptions;
    @Nonnull
    private final Connection connection;

    public TournamentListener(@Nonnull ShardManager jda, @Nonnull String stateDbLocation) throws SQLException {
        this.jda = jda;
        this.subscriptions = new ArrayList<>();

        connection = DriverManager.getConnection("jdbc:sqlite:" + stateDbLocation);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "DB sync scheduler"));

        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncSubscriptions();
            } catch (SQLException e) {
                log.error("DB sync failed", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Nonnull
    public synchronized Subscription subscribe(long tournamentId, long logChannelId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("INSERT INTO challonge_subscriptions (tournament_id, log_channel_id) VALUES (?, ?)");
        statement.setLong(1, tournamentId);
        statement.setLong(2, logChannelId);
        statement.execute();

        // Would have thrown if failed, we can confidently add this to the subscriptions and stay synced
        subscribeTo(tournamentId);
        Subscription sub = new Subscription(tournamentId, logChannelId);
        subscriptions.add(sub);
        return sub;
    }

    public void unsubscribe(@Nonnull Subscription subscription) throws SQLException {
        unsubscribe(subscription.getTournamentId());
    }

    public synchronized void unsubscribe(long tournamentId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("DELETE FROM challonge_subscriptions WHERE tournament_id = ?");
        statement.setLong(1, tournamentId);
        statement.execute();

        unsubscribeFrom(tournamentId);
        subscriptions.removeIf(subscription -> subscription.getTournamentId() == tournamentId);
    }

    @Nonnull
    public List<Subscription> getSubscriptions() {
        return Collections.synchronizedList(subscriptions);
    }

    @Nonnull
    public List<Subscription> findSubscriptionsByTournamentId(long tournamentId) {
        synchronized (subscriptions) {
            return subscriptions.stream().filter(sub -> sub.getTournamentId() == tournamentId).toList();
        }
    }

    @Nonnull
    public List<Subscription> findSubscriptionsByChannelId(long logChannelId) {
        synchronized (subscriptions) {
            return subscriptions.stream().filter(sub -> sub.getLogChannelId() == logChannelId).toList();
        }
    }

    /**
     * @return null if everything is a-ok. A string with an user-friendly message if the tournament is invalid.
     */
    @Nullable
    private String getTournamentInvalidationMessageIfInvalid(@Nonnull Tournament tournament) {
        if (tournament.getTournamentType() == TournamentType.FREE_FOR_ALL)
            return "I don't support free-for-all tournaments right now.";
        if (tournament.getGroupStagesEnabled()) return "I can't support multi-stage tournaments right now.";
        if (tournament.getTeams()) return "I don't support team tournaments right now.";

        return null;
    }

    /**
     * DB is always correct, we're adjusting ourselves to the DB. So we can do manual updates to the db.
     */
    private synchronized void syncSubscriptions() throws SQLException {
        // TODO: Should prolly log inconsistencies as INFO or sth

        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT tournament_id, log_channel_id FROM challonge_subscriptions");

        // Resetting subscription state
        // TODO: Efficiency?
        getSubscribedTournamentIds().forEach(this::unsubscribeFrom);
        subscriptions.clear();

        while (rs.next()) {
            long tournamentId = rs.getLong("tournament_id");
            long logChannelId = rs.getLong("log_channel_id");

            subscriptions.add(new Subscription(tournamentId, logChannelId));
            subscribeTo(tournamentId);
        }
    }

    public synchronized void shutdown() throws SQLException {
        connection.close();
        scheduler.shutdown();
    }

    @Override
    public void onGenericTournamentEvent(GenericTournamentEvent event) {
        Tournament tournament = event.getTournament();

        List<Subscription> subs = findSubscriptionsByTournamentId(tournament.getId());
        if (subs.isEmpty()) {
            log.error("Subscription for received event not found: {}", tournament.getId());
            return;
        }

        // Check validity before we log anything
        subs.stream().filter(Subscription::isDiscordInvalid).forEach(sub -> {
            try {
                unsubscribe(sub);
            } catch (SQLException e) {
                log.error("Unsubscribing because of invalid discord state failed with DB error", e);
            }
        });

        if (event instanceof TournamentDeletedEvent) {
            for (Subscription sub : subs) {
                sub.log("The tournament was deleted - I'll unsubscribe it from this channel now. If the tournament wasn't deleted or you think this is a bug, please contact my dev.").queue();

                try {
                    unsubscribe(sub);
                } catch (SQLException e) {
                    sub.log("Disregard, I can't unsubscribe because my database hates me apparently. Looks like my dev is going to have to do this manually, until then you might see some weird behaviour from me. I've already told them about it, but it won't hurt if you send them a message too.").queue();
                    log.error("Unsubscribing because of tournament deletion failed with DB error", e);
                }
            }
            return;
        }

        String validityMessage = getTournamentInvalidationMessageIfInvalid(tournament);
        if (validityMessage != null) {
            for (Subscription sub : subs) {
                sub.log(String.format("The tournament has been updated in a way that I don't support: %s If you think this is a bug, ask my dev. For now I'll unsubscribe the tournament from this channel.", validityMessage)).queue();

                try {
                    unsubscribe(sub);
                } catch (SQLException e) {
                    sub.log("Disregard, I can't unsubscribe because my database hates me apparently. Looks like my dev is going to have to do this manually, until then you might see some weird behaviour from me. I've already told them about it, but it won't hurt if you send them a message too.").queue();
                    log.error("!!!MANUAL DB EDIT NECESSARY!!! - Unsubscribe because of tournament deletion failed", e);
                }
            }
        }
    }

    @Override
    public void onTournamentStateChangedEvent(TournamentStateChangedEvent event) {
        if (event.getState() == TournamentState.UNDERWAY) {
            Tournament tournament = event.getTournament();
            tournament.getMatches().stream().filter(match -> match.getState().equals(MatchState.OPEN))
                    .forEach(match -> handleMatchOpening(tournament, match, false));
        }
    }

    @Override
    public void onMatchStateChangedEvent(MatchStateChangedEvent event) {
        if (event.getState().equals(MatchState.OPEN))
            handleMatchOpening(event.getTournament(), event.getMatch(), event.getPreviousState().equals(MatchState.COMPLETE));
        else if (event.getState().equals(MatchState.COMPLETE))
            handleMatchCompletion(event.getTournament(), event.getMatch());
    }

    private void handleMatchOpening(@Nonnull Tournament tournament, @Nonnull Match match, boolean reopened) {
        List<Subscription> subs = findSubscriptionsByTournamentId(tournament.getId());
        if (subs.isEmpty()) {
            log.error("Subscription for received event not found: {}", tournament.getId());
            return;
        }

        MessageBuilder message = new MessageBuilder();
        message.append("The match \"");

        long player1Id = match.getPlayer1Id();
        long player2Id = match.getPlayer2Id();

        Participant player1 = tournament.getParticipants().stream().filter(participant -> participant.getId() == player1Id).findAny().orElse(null);
        if (player1 == null) {
            log.error("Tournament {} doesn't house player 1 participant {}", tournament.getId(), player1Id);
            return;
        }

        Participant player2 = tournament.getParticipants().stream().filter(participant -> participant.getId() == player2Id).findAny().orElse(null);
        if (player2 == null) {
            log.error("Tournament {} doesn't house player 2 participant {}", tournament.getId(), player2Id);
            return;
        }

        message.append(player1.getDisplayName());
        message.append("\" vs \"");
        message.append(player2.getDisplayName());

        message.append("\" with the identifier \"").append(match.getIdentifier()).append("\" ");

        message.append(reopened ?
                "has been reopened! "
                : "is now open! ");

        message.append("Good luck to the competitors!");

        subs.forEach(sub -> sub.log(message.build()).queue());
    }

    private void handleMatchCompletion(@Nonnull Tournament tournament, @Nonnull Match match) {
        List<Subscription> subs = findSubscriptionsByTournamentId(match.getTournamentId());
        if (subs.isEmpty()) {
            log.error("Subscription for received event not found: {}", tournament.getId());
            return;
        }

        String scoresCsv = match.getScoresCsv().replace(",", ", ");

        MessageBuilder message = new MessageBuilder();
        message.append("The match \"");

        long player1Id = match.getPlayer1Id();
        long player2Id = match.getPlayer2Id();

        Participant player1 = tournament.getParticipants().stream().filter(participant -> participant.getId() == player1Id).findAny().orElse(null);
        if (player1 == null) {
            log.error("Tournament {} doesn't house player 1 participant {}", tournament.getId(), player1Id);
            return;
        }

        Participant player2 = tournament.getParticipants().stream().filter(participant -> participant.getId() == player2Id).findAny().orElse(null);
        if (player2 == null) {
            log.error("Tournament {} doesn't house player 2 participant {}", tournament.getId(), player2Id);
            return;
        }

        long winnerId = match.getWinnerId();

        Participant winner = player1Id == winnerId ?
                player1
                : player2Id == winnerId ?
                player2
                : null;

        message.append(player1.getDisplayName());
        message.append("\" vs \"");
        message.append(player2.getDisplayName());

        message.append("\" has been decided! The scores were ");
        message.append(scoresCsv);

        if (winner != null) {
            message.append(", making \"");
            message.append(winner.getDisplayName());
            message.append("\" the winner");
        }

        message.append(". Ggs to both players!");

        subs.forEach(sub -> sub.log(message.build()).queue());
    }

    @Override
    public void onTournamentCompletedAtChangedEvent(TournamentCompletedAtChangedEvent event) {
        Tournament tournament = event.getTournament();

        List<Subscription> subs = findSubscriptionsByTournamentId(tournament.getId());
        if (subs.isEmpty()) {
            log.error("Subscription for received event not found: {}", tournament.getId());
            return;
        }

        if (event.getPreviousCompletedAt() == null && event.getCompletedAt() != null) {
            String placings = tournament.getParticipants().stream().sorted(Comparator.comparingInt(Participant::getFinalRank)).limit(8)
                    .map(p -> String.format("• %d - %s", p.getFinalRank(), p.getDisplayName())).collect(Collectors.joining("\n"));

            subs.forEach(sub -> sub.log(String.format("The tournament is completed and the final results are in! Here are the top competitors:```%n%s%n```" +
                    "See the full standings at https://challonge.com/%s/standings", placings, tournament.getUrl())).queue());
        }
    }

    public class Subscription {
        private final long tournamentId;
        private final long logChannelId;

        public Subscription(long tournamentId, long logChannelId) {
            this.tournamentId = tournamentId;
            this.logChannelId = logChannelId;
        }

        public long getTournamentId() {
            return tournamentId;
        }

        public long getLogChannelId() {
            return logChannelId;
        }

        public boolean isDiscordInvalid() {
            TextChannel channel = jda.getTextChannelById(logChannelId);
            if (channel == null) return true;
            return !channel.canTalk();
        }

        @CheckReturnValue
        @Nonnull
        public RestAction<Message> log(@Nonnull String message) {
            return log(new MessageBuilder(message).build());
        }

        @CheckReturnValue
        @Nonnull
        public RestAction<Message> log(@Nonnull Message message) {
            TextChannel channel = jda.getTextChannelById(logChannelId);
            if (channel == null) {
                log.error("Channel not in cache - {}", logChannelId);
                throw new IllegalStateException("Channel not in cache");
            }

            return channel.sendMessage(message);
        }
    }
}
