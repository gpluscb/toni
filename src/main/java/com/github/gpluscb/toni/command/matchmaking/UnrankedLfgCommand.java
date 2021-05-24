package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
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

        try {
            UnrankedManager.MatchmakingConfig config = manager.loadMatchmakingConfig(ctx.getEvent().getGuild().getIdLong());
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
            ctx.reply(String.format("%s, %s is looking for a game for %s. React with %s to accept. %2$s, you can react with %s to cancel.",
                    MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId), MiscUtil.durationToString(duration), Constants.FENCER, Constants.CROSS_MARK))
                    .mentionRoles(roleId).mentionUsers(userId).queue(msg -> {
                TextChannel textChannel = msg.getTextChannel();

                if (textChannel.getGuild().getSelfMember().hasPermission(msg.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
                    msg.addReaction(Constants.FENCER).queue();
                    msg.addReaction(Constants.CROSS_MARK).queue();
                }

                JDA jda = msg.getJDA();
                long msgId = msg.getIdLong();
                long channelId = textChannel.getIdLong();

                AtomicBoolean wasCancelled = new AtomicBoolean(false);

                waiter.waitForEvent(MessageReactionAddEvent.class,
                        e -> checkMatchmakingReaction(e, userId, wasCancelled),
                        e -> executeMatchmakingReaction(e, roleId, userId, wasCancelled),
                        duration.toMillis(), TimeUnit.MILLISECONDS,
                        FailLogger.logFail(() -> {
                            if (wasCancelled.get()) return;
                            TextChannel channel = jda.getTextChannelById(channelId);
                            if (channel == null)
                                log.warn("TextChannel for timeout action not in cache - {}", channelId);
                            else executeMatchmakingReactionTimeout(channel, msgId, userId, roleId);
                        }));
            });
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went wrong talking to my database. I've told my dev about this, if this keeps happening you should give them some context too.").queue();
        }
    }

    private boolean checkMatchmakingReaction(@Nonnull MessageReactionAddEvent e, long userId, @Nonnull AtomicBoolean wasCancelled) {
        if (wasCancelled.get()) return false;
        long eventUserId = e.getUserIdLong();
        if (eventUserId == botId) return false;
        String reactionName = e.getReactionEmote().getName();

        if (reactionName.equals(Constants.FENCER)) {
            if (eventUserId == userId) {
                e.getChannel().sendMessage("If you are trying to play with yourself, that's ok too. But there's no need to ping matchmaking for that my friend.").queue();
                return false;
            }

            return true;
        }

        return reactionName.equals(Constants.CROSS_MARK);
    }

    private void executeMatchmakingReaction(@Nonnull MessageReactionAddEvent event, long roleId, long userId, @Nonnull AtomicBoolean wasCancelled) {
        String reactionName = event.getReactionEmote().getName();
        MessageChannel originalChannel = event.getChannel();

        long challengerId = event.getUserIdLong();
        long originalMessageId = event.getMessageIdLong();

        if (reactionName.equals(Constants.CROSS_MARK)) {
            wasCancelled.set(true);
            event.getChannel().editMessageById(originalMessageId, String.format("%s, %s was looking for a game, but cancelled their search.",
                    MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId)))
                    .mentionRoles(roleId).mentionUsers(userId)
                    .queue();

            MiscUtil.clearReactionsOrRemoveOwnReactions(originalChannel, originalMessageId, Constants.CROSS_MARK, Constants.FENCER).queue();
            return;
        }

        // TODO: Should we keep that "if you want me to disable" stuff to just the main message?
        originalChannel.sendMessage(String.format("%s, %s wants to play with you." +
                        " If you want me to disable the reaction on the original message, react with %s within three minutes.",
                MiscUtil.mentionUser(userId), MiscUtil.mentionUser(challengerId), Constants.CHECK_MARK))
                .mentionUsers(userId, challengerId).queue(msg -> {
            TextChannel textChannel = msg.getTextChannel();
            long msgId = msg.getIdLong();
            long channelId = textChannel.getIdLong();
            JDA jda = msg.getJDA();

            if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_ADD_REACTION))
                msg.addReaction(Constants.CHECK_MARK).queue();

            waiter.waitForEvent(
                    MessageReactionAddEvent.class,
                    e -> checkConfirmReaction(e, userId),
                    e -> {
                        TextChannel channel = jda.getTextChannelById(channelId);
                        if (channel == null) log.warn("TextChannel for matchmaking reaction not in cache - {}", channelId);
                        else
                            executeConfirmReaction(channel, msgId, originalMessageId, roleId, userId, challengerId, wasCancelled);
                    },
                    3, TimeUnit.MINUTES,
                    FailLogger.logFail(() -> {
                        TextChannel channel = jda.getTextChannelById(channelId);
                        if (channel == null) log.warn("TextChannel for timeout action not in cache - {}", channelId);
                        else executeConfirmTimeout(channel, msgId, userId, challengerId);
                    })
            );
        });
    }

    private boolean checkConfirmReaction(@Nonnull MessageReactionAddEvent e, long userId) {
        return e.getUserIdLong() == userId && e.getReactionEmote().getName().equals(Constants.CHECK_MARK);
    }

    private void executeConfirmReaction(@Nonnull TextChannel channel, long messageId, long originalMessageId, long roleId, long userId, long challengerId, @Nonnull AtomicBoolean wasCancelled) {
        wasCancelled.set(true);

        channel.editMessageById(originalMessageId, String.format("%s, %s was looking for a game, but they found someone.",
                MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId))).mentionRoles(roleId).mentionUsers(userId).queue();
        MiscUtil.clearReactionsOrRemoveOwnReactions(channel, originalMessageId, Constants.CROSS_MARK, Constants.FENCER).queue();
        MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CHECK_MARK).queue();

        channel.editMessageById(messageId, String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(userId), MiscUtil.mentionUser(challengerId)))
                .mentionUsers(userId, challengerId).queue();
    }

    private void executeConfirmTimeout(@Nonnull TextChannel channel, long messageId, long userId, long challengerId) {
        channel.editMessageById(messageId, String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(userId), MiscUtil.mentionUser(challengerId)))
                .mentionUsers(userId, challengerId).queue();
        MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CROSS_MARK, Constants.FENCER).queue();
    }

    private void executeMatchmakingReactionTimeout(@Nonnull TextChannel channel, long messageId, long userId, long roleId) {
        channel.editMessageById(messageId, String.format("%s, %s was looking for a game.",
                MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId))).mentionRoles(roleId).mentionUsers(userId).queue();
        MiscUtil.clearReactionsOrRemoveOwnReactions(channel, messageId, Constants.CROSS_MARK, Constants.FENCER).queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"unranked", "lfg"};
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
                "Aliases: `lfg`, `unranked`";
    }
}
