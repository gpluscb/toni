package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.*;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: Don't allow multiple lfgs at the same time
public class UnrankedLfgCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedLfgCommand.class);

    @Nonnull
    private final UnrankedManager manager;
    @Nonnull
    private final EventWaiter waiter;
    private final long botId;

    public UnrankedLfgCommand(@Nonnull UnrankedManager manager, @Nonnull EventWaiter waiter, long botId) {
        this.manager = manager;
        this.waiter = waiter;
        this.botId = botId;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers. There is only us two in our DMs. And it's nice that you want to play with me, but sorry I don't have hands.").queue();
            return;
        }

        UnrankedManager.MatchmakingConfig config;
        try {
            config = manager.loadMatchmakingConfig(ctx.getEvent().getGuild().getIdLong());
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went wrong talking to my database. I've told my dev about this, if this keeps happening you should give them some context too.").queue();
            return;
        }

        if (config == null) {
            ctx.reply("Matchmaking is not set up in this server. A mod can use the `unrankedcfg` command to configure matchmaking.").queue();
            return;
        }

        Long lfgChannelId = config.getChannelId();
        if (lfgChannelId != null && ctx.getChannel().getIdLong() != lfgChannelId) {
            ctx.reply(String.format("This command is configured to only work in %s.", MiscUtil.mentionChannel(lfgChannelId))).queue();
            return;
        }

        Duration duration;
        if (ctx.getArgNum() > 0) {
            duration = MiscUtil.parseDuration(ctx.getArgsFrom(0));
            if (duration == null) {
                ctx.reply("The given duration was not a valid duration. An example duration is `1h 30m`.").queue();
                return;
            }

            if (duration.compareTo(Duration.ofHours(5)) > 0) {
                ctx.reply("The maximum duration is 5h.").queue();
                return;
            }

            if (duration.compareTo(Duration.ofMinutes(10)) < 0) {
                ctx.reply("The minimum duration is 10 minutes.").queue();
                return;
            }
        } else {
            duration = Duration.ofHours(2);
        }

        long userId = ctx.getAuthor().getIdLong();
        long roleId = config.getLfgRoleId();

        Message start = new MessageBuilder(String.format("%s, %s is looking for a game for %s. React with %s to accept. %2$s, you can react with %s to cancel.",
                MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId), MiscUtil.durationToString(duration), Constants.FENCER, Constants.CROSS_MARK))
                .mentionRoles(roleId).mentionUsers(userId).build();

        ReactionHandler handler = new ReactionHandler(userId, roleId);
        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .registerButton(Constants.FENCER, handler::fightReaction)
                .registerButton(Constants.CROSS_MARK, handler::cancelReaction)
                .setStart(start)
                .setTimeoutAction(handler::timeout)
                .build();

        menu.displayReplying(ctx.getMessage());
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"unranked", "lfg", "fight", "fite"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Pings the matchmaking role and lets you know if someone wants to play for a given duration. Usage: `unranked [DURATION]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`lfg [DURATION (default 2h)]`\n" +
                "Pings the matchmaking role and asks players to react if they want to play. Notifies you when they react within the given duration." +
                " The duration can have the format `Xh Xm Xs`, and it has to be between 10m and 5h.\n" +
                "Aliases: `lfg`, `unranked`, `fight`, `fite`";
    }

    private class ReactionHandler {
        @Nonnull
        private final AtomicBoolean isCancelled;
        private final long originalAuthorId;
        private final long matchmakingRoleId;

        private ReactionHandler(long originalAuthorId, long matchmakingRoleId) {
            this.isCancelled = new AtomicBoolean(false);
            this.originalAuthorId = originalAuthorId;
            this.matchmakingRoleId = matchmakingRoleId;
        }

        // TODO: Don't allow someone to challenge twice
        @Nullable
        public Message fightReaction(@Nonnull MessageReactionAddEvent e) {
            if (isCancelled.get()) return null;
            long eventUserId = e.getUserIdLong();
            if (eventUserId == botId) return null;

            if (eventUserId == originalAuthorId) {
                e.getChannel().sendMessage("If you are trying to play with yourself, that's ok too. But there's no need to ping matchmaking for that my friend.").queue();
                return null;
            }

            MessageChannel originalChannel = e.getChannel();

            long challengerId = e.getUserIdLong();
            long originalMessageId = e.getMessageIdLong();

            Message start = new MessageBuilder(String.format("%s, %s wants to play with you." +
                            " If you want me to disable the reaction on the original message, react with %s within three minutes.",
                    MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId), Constants.CHECK_MARK))
                    .mentionUsers(originalAuthorId, challengerId).build();

            // TODO: Should we keep that "if you want me to disable" stuff to just the main message?
            DisableReactionHandler handler = new DisableReactionHandler(originalMessageId, challengerId);
            ButtonActionMenu menu = new ButtonActionMenu.Builder()
                    .setEventWaiter(waiter)
                    .setDeletionButton(null)
                    .registerButton(Constants.CHECK_MARK, handler::confirmReaction)
                    .setStart(start)
                    .addUsers(originalAuthorId)
                    .setTimeout(3, TimeUnit.MINUTES)
                    .setTimeoutAction(handler::timeout)
                    .build();

            menu.displayReplying(originalChannel, originalMessageId);

            return null;
        }

        @Nullable
        public Message cancelReaction(@Nonnull MessageReactionAddEvent e) {
            if (e.getUserIdLong() != originalAuthorId) return null;

            isCancelled.set(true);

            MiscUtil.clearReactionsOrRemoveOwnReactions(e.getChannel(), e.getMessageIdLong(), Constants.CROSS_MARK, Constants.FENCER).queue();

            return new MessageBuilder(String.format("%s, %s was looking for a game, but cancelled their search.",
                    MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                    .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId).build();
        }

        public void timeout(@Nullable MessageChannel channel, long messageId) {
            if (channel != null && !isCancelled.get()) {
                channel.editMessageById(messageId, String.format("%s, %s was looking for a game.",
                        MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                        .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                        .queue();
                MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CROSS_MARK, Constants.FENCER).queue();
            }
        }

        private class DisableReactionHandler {
            private final long originalMessageId;
            private final long challengerId;

            private DisableReactionHandler(long originalMessageId, long challengerId) {
                this.originalMessageId = originalMessageId;
                this.challengerId = challengerId;
            }

            @Nullable
            public Message confirmReaction(@Nonnull MessageReactionAddEvent e) {
                MessageChannel channel = e.getChannel();
                long messageId = e.getMessageIdLong();

                isCancelled.set(true);

                channel.editMessageById(originalMessageId, String.format("%s, %s was looking for a game, but they found someone.",
                        MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                        .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                        .queue();
                MiscUtil.clearReactionsOrRemoveOwnReactions(channel, originalMessageId, Constants.CROSS_MARK, Constants.FENCER).queue();
                MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CHECK_MARK).queue();

                return new MessageBuilder(String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                        .mentionUsers(originalAuthorId, challengerId).build();
            }

            public void timeout(@Nullable MessageChannel channel, long messageId) {
                if (channel == null) return;

                channel.editMessageById(messageId, String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                        .mentionUsers(originalAuthorId, challengerId).queue();
                MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CROSS_MARK, Constants.FENCER).queue();
            }
        }
    }
}
