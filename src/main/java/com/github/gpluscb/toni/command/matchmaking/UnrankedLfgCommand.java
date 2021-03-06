package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.*;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UnrankedLfgCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedLfgCommand.class);

    @Nonnull
    private final String supportServer;

    @Nonnull
    private final UnrankedManager manager;
    @Nonnull
    private final EventWaiter waiter;

    @Nonnull
    private final Set<PairNonnull<Long, Long>> currentlyLfgPerGuild;

    public UnrankedLfgCommand(@Nonnull String supportServer, @Nonnull UnrankedManager manager, @Nonnull EventWaiter waiter) {
        this.supportServer = supportServer;
        this.manager = manager;
        this.waiter = waiter;
        currentlyLfgPerGuild = new HashSet<>();
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

        long guildId = ctx.getEvent().getGuild().getIdLong();
        long userId = ctx.getAuthor().getIdLong();
        long roleId = config.getLfgRoleId();

        synchronized (currentlyLfgPerGuild) {
            if (currentlyLfgPerGuild.contains(new PairNonnull<>(guildId, userId))) {
                ctx.reply("You are already looking for a game. If you believe this is a bug, contact my dev.").queue();
                return;
            }

            currentlyLfgPerGuild.add(new PairNonnull<>(guildId, userId));
        }

        Message start = new MessageBuilder(String.format("%s, %s is looking for a game for %s. %2$s, you can click on the %s button to cancel.",
                MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId), MiscUtil.durationToString(duration), Constants.CROSS_MARK))
                .mentionRoles(roleId).mentionUsers(userId).build();

        ButtonHandler handler = new ButtonHandler(guildId, userId, roleId);
        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .registerButton(Button.success("fight", Emoji.fromUnicode(Constants.FENCER)).withLabel("Fight"), handler::fightButton)
                .registerButton(Button.danger("cancel", Emoji.fromUnicode(Constants.CROSS_MARK)), handler::cancelButton)
                .setStart(start)
                .setTimeout(duration.getSeconds(), TimeUnit.SECONDS)
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
        return "**[BETA]** Pings the matchmaking role and lets you know if someone wants to play for a given duration. Usage: `unranked [DURATION]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`lfg [DURATION (default 2h)]`\n" +
                "Pings the matchmaking role and asks players to react if they want to play. Notifies you when they react within the given duration." +
                " The duration can have the format `Xh Xm Xs`, and it has to be between 10m and 5h.\n" +
                "Aliases: `lfg`, `unranked`, `fight`, `fite`\n" +
                String.format("This command is in **BETA**. If you have feedback, bugs, or other issues, please go to [my support server](%s).", supportServer);
    }

    private class ButtonHandler {
        private final long guildId;
        private final long originalAuthorId;
        private final long matchmakingRoleId;

        @Nonnull
        private final Set<Long> currentlyChallenging;

        private ButtonHandler(long guildId, long originalAuthorId, long matchmakingRoleId) {
            this.guildId = guildId;
            this.originalAuthorId = originalAuthorId;
            this.matchmakingRoleId = matchmakingRoleId;
            currentlyChallenging = new HashSet<>();
        }

        @Nullable
        public Message fightButton(@Nonnull ButtonClickEvent e) {
            synchronized (currentlyLfgPerGuild) {
                // Was it cancelled?
                if (!currentlyLfgPerGuild.contains(new PairNonnull<>(guildId, originalAuthorId))) return null;
            }

            long challengerId = e.getUser().getIdLong();
            if (challengerId == originalAuthorId) {
                e.getChannel().sendMessage("If you are trying to play with yourself, that's ok too. But there's no need to ping matchmaking for that my friend.").queue();
                return null;
            }

            synchronized (currentlyChallenging) {
                if (currentlyChallenging.contains(challengerId)) return null;

                currentlyChallenging.add(challengerId);
            }

            e.deferEdit().queue();

            MessageChannel originalChannel = e.getChannel();

            long originalMessageId = e.getMessageIdLong();

            Message start = new MessageBuilder(String.format("%s, %s wants to play with you." +
                            " If you want me to disable the search on the original message," +
                            " click on the %s button within three minutes.",
                    MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId), Constants.CHECK_MARK))
                    .mentionUsers(originalAuthorId, challengerId)
                    .build();

            // TODO: Should we keep that "if you want me to disable" stuff to just the main message?
            DisableButtonHandler handler = new DisableButtonHandler(originalMessageId, challengerId);
            ButtonActionMenu menu = new ButtonActionMenu.Builder()
                    .setEventWaiter(waiter)
                    .setDeletionButton(null)
                    .registerButton(Button.success("confirm", Constants.CHECK_MARK), handler::confirmReaction)
                    .setStart(start)
                    .addUsers(originalAuthorId)
                    .setTimeout(3, TimeUnit.MINUTES)
                    .setTimeoutAction(handler::timeout)
                    .build();

            menu.displayReplying(originalChannel, originalMessageId);

            return null;
        }

        @Nullable
        public Message cancelButton(@Nonnull ButtonClickEvent e) {
            if (e.getUser().getIdLong() != originalAuthorId) return null;

            e.deferEdit().queue();

            synchronized (currentlyLfgPerGuild) {
                currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
            }

            return new MessageBuilder(String.format("%s, %s was looking for a game, but cancelled their search.",
                    MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                    .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                    .setActionRows()
                    .build();
        }

        public void timeout(@Nullable MessageChannel channel, long messageId) {
            synchronized (currentlyLfgPerGuild) {
                currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
            }

            if (channel != null) {
                channel.editMessageById(messageId, String.format("%s, %s was looking for a game.",
                        MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                        .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                        .setActionRows()
                        .queue();
            }
        }

        private class DisableButtonHandler {
            private final long originalMessageId;
            private final long challengerId;

            private DisableButtonHandler(long originalMessageId, long challengerId) {
                this.originalMessageId = originalMessageId;
                this.challengerId = challengerId;
            }

            @Nullable
            public Message confirmReaction(@Nonnull ButtonClickEvent e) {
                e.deferEdit().queue();

                MessageChannel channel = e.getChannel();

                synchronized (currentlyLfgPerGuild) {
                    // This is the Object variant, not index
                    currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
                }

                channel.editMessageById(originalMessageId, String.format("%s, %s was looking for a game, but they found someone.",
                        MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                        .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                        .setActionRows()
                        .queue();

                return new MessageBuilder(String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                        .mentionUsers(originalAuthorId, challengerId)
                        .setActionRows()
                        .build();
            }

            public void timeout(@Nullable MessageChannel channel, long messageId) {
                if (channel == null) return;

                channel.editMessageById(messageId, String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                        .mentionUsers(originalAuthorId, challengerId)
                        .setActionRows()
                        .queue();
            }
        }
    }
}
