package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.db.DBManager;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.PairNonnull;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UnrankedLfgCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedLfgCommand.class);

    @Nonnull
    private final DBManager manager;
    @Nonnull
    private final EventWaiter waiter;

    @Nonnull
    private final Set<PairNonnull<Long, Long>> currentlyLfgPerGuild;

    public UnrankedLfgCommand(@Nonnull DBManager manager, @Nonnull EventWaiter waiter) {
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

        Guild guild = ctx.getEvent().getGuild();

        DBManager.MatchmakingConfig config;
        try {
            // Already checked for isFromGuild
            //noinspection ConstantConditions
            config = manager.loadMatchmakingConfig(guild.getIdLong());
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went wrong talking to my database. I've told my dev about this, if this keeps happening you should give them some context too.").queue();
            return;
        }

        if (config == null) {
            ctx.reply("Matchmaking is not set up in this server. A mod can use the `unrankedcfg` command to configure matchmaking.").queue();
            return;
        }

        Long lfgChannelId = config.channelId();
        if (lfgChannelId != null && ctx.getChannel().getIdLong() != lfgChannelId) {
            ctx.reply(String.format("This command is configured to only work in %s.", MiscUtil.mentionChannel(lfgChannelId))).queue();
            return;
        }

        Duration duration = Duration.ofHours(2);

        OptionMapping durationMapping = ctx.getOption("duration");
        if (durationMapping != null) {
            duration = MiscUtil.parseDuration(durationMapping.getAsString());

            if (duration == null) {
                ctx.reply("The given duration was not a valid duration. An example duration is `1h 30m`.").queue();
                return;
            }
        }

        if (duration.compareTo(Duration.ofHours(5)) > 0) {
            ctx.reply("The maximum duration is 5h.").queue();
            return;
        }

        if (duration.compareTo(Duration.ofMinutes(10)) < 0) {
            ctx.reply("The minimum duration is 10 minutes.").queue();
            return;
        }

        long guildId = guild.getIdLong();
        long userId = ctx.getUser().getIdLong();
        long roleId = config.lfgRoleId();

        synchronized (currentlyLfgPerGuild) {
            if (currentlyLfgPerGuild.contains(new PairNonnull<>(guildId, userId))) {
                ctx.reply("You are already looking for a game. If you believe this is a bug, contact my dev.").queue();
                return;
            }

            currentlyLfgPerGuild.add(new PairNonnull<>(guildId, userId));
        }

        MessageCreateData start = new MessageCreateBuilder()
                .setContent(String.format("%s, %s is looking for a game for %s (until %s). %2$s, you can click on the %s button to cancel.",
                        MiscUtil.mentionRole(roleId),
                        MiscUtil.mentionUser(userId),
                        MiscUtil.durationToString(duration),
                        TimeFormat.RELATIVE.after(duration),
                        Constants.CROSS_MARK))
                .mentionRoles(roleId).mentionUsers(userId).build();

        ButtonHandler handler = new ButtonHandler(guildId, userId, roleId);
        ButtonActionMenu menu = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(waiter)
                        .setTimeout(duration.getSeconds(), TimeUnit.SECONDS)
                        .build())
                .setDeletionButton(null)
                .registerButton(Button.success("fight", Emoji.fromUnicode(Constants.FENCER)).withLabel("Fight"), handler::fightButton)
                .registerButton(Button.danger("cancel", Emoji.fromUnicode(Constants.CROSS_MARK)), handler::cancelButton)
                .setStart(start)
                .setOnTimeout(handler::timeout)
                .build());

        menu.displaySlashReplying(ctx.getEvent());
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY})
                .setShortHelp("Pings the matchmaking role and lets you know if someone wants to play for a given duration.")
                .setDetailedHelp("""
                        Pings the matchmaking role and asks players to react if they want to play. Notifies you when they react within the given duration.
                        Slash command options:
                        • (Optional) `duration`: The duration for which you are looking for a game. The duration should have the format `Xh Xm Xs`, and it has to be between 10m and 5h. Default is two hours.""")
                .setCommandData(Commands.slash("lfg", "Pings matchmaking and lets you know if someone is available to play")
                        .addOption(OptionType.STRING, "duration", "How long you are looking for a game. Default is two hours", false))
                .build();
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

        @Nonnull
        public ActionMenu.MenuAction fightButton(@Nonnull ButtonInteractionEvent e) {
            synchronized (currentlyLfgPerGuild) {
                // Was it cancelled?
                if (!currentlyLfgPerGuild.contains(new PairNonnull<>(guildId, originalAuthorId)))
                    return ActionMenu.MenuAction.CANCEL;
            }

            long challengerId = e.getUser().getIdLong();
            if (challengerId == originalAuthorId) {
                e.reply("If you are trying to play with yourself, that's ok too. But there's no need to ping matchmaking for that my friend.").queue();
                return ActionMenu.MenuAction.CONTINUE;
            }

            synchronized (currentlyChallenging) {
                if (currentlyChallenging.contains(challengerId))
                    return ActionMenu.MenuAction.CONTINUE;

                currentlyChallenging.add(challengerId);
            }

            e.deferEdit().queue();

            MessageChannel originalChannel = e.getChannel();

            long originalMessageId = e.getMessageIdLong();

            MessageCreateData start = new MessageCreateBuilder()
                    .setContent(String.format("%s, %s wants to play with you." +
                                    " If you want me to disable the search on the original message," +
                                    " click on the %s button within three minutes.",
                            MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId), Constants.CHECK_MARK))
                    .mentionUsers(originalAuthorId, challengerId)
                    .build();

            // TODO: Should we keep that "if you want me to disable" stuff to just the main message?
            DisableButtonHandler handler = new DisableButtonHandler(originalMessageId, challengerId);
            ButtonActionMenu menu = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                    .setActionMenuSettings(new ActionMenu.Settings.Builder()
                            .setWaiter(waiter)
                            .setTimeout(3, TimeUnit.MINUTES)
                            .build())
                    .setDeletionButton(null)
                    .registerButton(Button.success("confirm", Constants.CHECK_MARK), handler::confirmButton)
                    .setStart(start)
                    .addUsers(originalAuthorId)
                    .setOnTimeout(handler::timeout)
                    .build());

            menu.displayReplying(originalChannel, originalMessageId);

            return ActionMenu.MenuAction.CONTINUE;
        }

        @Nonnull
        public ActionMenu.MenuAction cancelButton(@Nonnull ButtonInteractionEvent e) {
            if (e.getUser().getIdLong() != originalAuthorId) return ActionMenu.MenuAction.CONTINUE;

            synchronized (currentlyLfgPerGuild) {
                currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
            }

            e.editMessage(new MessageEditBuilder()
                            .setContent(String.format("%s, %s was looking for a game, but cancelled their search.",
                                    MiscUtil.mentionRole(matchmakingRoleId),
                                    MiscUtil.mentionUser(originalAuthorId)))
                            .mentionRoles(matchmakingRoleId)
                            .mentionUsers(originalAuthorId)
                            .build())
                    .setComponents()
                    .queue();

            return ActionMenu.MenuAction.CANCEL;
        }

        public void timeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
            synchronized (currentlyLfgPerGuild) {
                currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
            }

            MessageChannel channel = event.getChannel();
            if (channel == null) return;

            channel.editMessageById(event.getMessageId(), String.format("%s, %s was looking for a game.",
                            MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                    .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                    .setComponents()
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        }

        private class DisableButtonHandler {
            private final long originalMessageId;
            private final long challengerId;

            private DisableButtonHandler(long originalMessageId, long challengerId) {
                this.originalMessageId = originalMessageId;
                this.challengerId = challengerId;
            }

            @Nonnull
            public ActionMenu.MenuAction confirmButton(@Nonnull ButtonInteractionEvent e) {
                MessageChannel channel = e.getChannel();

                synchronized (currentlyLfgPerGuild) {
                    currentlyLfgPerGuild.remove(new PairNonnull<>(guildId, originalAuthorId));
                }

                channel.editMessageById(originalMessageId, String.format("%s, %s was looking for a game, but they found someone.",
                                MiscUtil.mentionRole(matchmakingRoleId), MiscUtil.mentionUser(originalAuthorId)))
                        .mentionRoles(matchmakingRoleId).mentionUsers(originalAuthorId)
                        .setComponents()
                        .queue();

                e.editMessage(new MessageEditBuilder()
                                .setContent(String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                                .mentionUsers(originalAuthorId, challengerId)
                                .build())
                        .setComponents()
                        .queue();

                return ActionMenu.MenuAction.CANCEL;
            }

            public void timeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
                MessageChannel channel = event.getChannel();
                if (channel == null) return;

                channel.editMessageById(event.getMessageId(), String.format("%s, %s wants to play with you.", MiscUtil.mentionUser(originalAuthorId), MiscUtil.mentionUser(challengerId)))
                        .mentionUsers(originalAuthorId, challengerId)
                        .setComponents()
                        .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
            }
        }
    }
}
