package com.github.gpluscb.toni.command.challonge;

import com.github.gpluscb.challonge_listener.ChallongeExtension;
import com.github.gpluscb.toni.challonge.TournamentListener;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.util.FailLogger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

public class SubscribeCommand implements Command {
    private final static Logger log = LogManager.getLogger(SubscribeCommand.class);

    @Nonnull
    private final ChallongeExtension challonge;
    @Nonnull
    private final TournamentListener listener;

    public SubscribeCommand(@Nonnull ChallongeExtension challonge, @Nonnull TournamentListener listener) {
        this.challonge = challonge;
        this.listener = listener;
    }

    // TODO: No args variant
    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers, sorry.").queue();
            return;
        }

        if (!ctx.memberHasManageChannelsPermission() && !ctx.memberHasBotAdminPermission()) {
            ctx.reply("Hmmm, I don't think I can trust you quite yet... You'll need the `Manage Channels` permission to use this.").queue();
            return;
        }

        if (ctx.getArgs().size() != 2) {
            ctx.reply("Wrong number of arguments. I need the tournaments url and the channel you want to have the logs in. Use `help subscribe` for help.").queue();
            return;
        }

        String url = ctx.getArg(0);

        TextChannel channel = ctx.getChannelMentionArg(1);
        if (channel == null) {
            ctx.reply(String.format("Please mention a channel as the second argument, so that I can click on it. You know, the %s thing.", ctx.getEvent().getTextChannel())).queue();
            return;
        }

        Guild guild = ctx.getEvent().getGuild();
        if (!channel.getGuild().equals(guild)) {
            ctx.reply("I don't know what to say... That channel is not in this server, I'm pretty sure. Why would you do such a thing?").queue();
            return;
        }

        // One subscription per channel
        List<TournamentListener.Subscription> subs = listener.getSubscriptions();
        long channelId = channel.getIdLong();
        boolean channelOccupied = subs.stream().mapToLong(TournamentListener.Subscription::getLogChannelId)
                .anyMatch(id -> channelId == id); // Check if given channel already is subscribed to a thing

        if (channelOccupied) {
            ctx.reply("That channel is already subscribed to a tournament! If you think this is a bug, please tell my dev, they'll be happy to hear about it.").queue();
            return;
        }

        if (!channel.canTalk()) {
            ctx.reply("I don't have permission to send messages in that channel.").queue();
            return;
        }

        challonge.getTournamentOrNull(url, FailLogger.logFail(tournament -> {
            if (tournament == null) {
                ctx.reply("I didn't find any tournament for that url. The part of the url I'd need would, for example, be `example` for `challonge.com/example`.").queue();
                return;
            }

            long tournamentId = tournament.getId();

            // One subscription per tournament per server
            boolean tournamentSubscribedToInServer = subs.stream().filter(sub -> guild.getTextChannelById(sub.getLogChannelId()) != null) // Only stuff in this guild
                    .mapToLong(TournamentListener.Subscription::getTournamentId).anyMatch(id -> id == tournamentId);

            if (tournamentSubscribedToInServer) {
                ctx.reply("This tournament is already subscribed to in this server. If you think this is a bug, contact my dev please.").queue();
                return;
            }

            try {
                TournamentListener.Subscription sub = listener.subscribe(tournamentId, channelId);

                ctx.reply("I've done it. You should get logs about matches and such and such now.").queue();
                sub.log(String.format("This channel is now subscribed to a tournament. I will post some automated updates about https://challonge.com/%s here during the tournament.", url)).queue();
            } catch (SQLException e) {
                log.error("DB: failure to subscribe", e);
                ctx.reply("Looks like my database doesn't really work right now, so I can't subscribe right now... I'll tell my dev about that, but you can give them some context too.").queue();
            }
        }), FailLogger.logFail(failure -> {
            log.catching(failure);
            ctx.reply("The request to challonge failed. If this keeps happening please tell my dev about what you're doing. I've already sent them a report, but it can't hurt to get more data").queue();
        }));
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"subscribe", "sub", "link"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Subscribes a channel in this server to receive automated updates about a tournament on challonge. Usage: `subscribe <END OF TOURNAMENT URL> <CHANNEL MENTION>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`sub[scribe]|link <END OF TOURNAMENT URL> <CHANNEL MENTION>`\nThe bot will look at the specified tournament, and send updates in the specified channel. " +
                "The part of the url I'd need would, for example, be `example` for `challonge.com/example`.\n" +
                "Unfortunately, I can't really support free for all, team, and multistage tournaments as of now.";
    }
}
