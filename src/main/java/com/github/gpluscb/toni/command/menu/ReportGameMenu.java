package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ConfirmableSelectionActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.SmashSet;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static net.dv8tion.jda.api.interactions.components.selections.SelectOption.LABEL_MAX_LENGTH;

public class ReportGameMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ConfirmableSelectionActionMenu<Long> underlying;

    @Nullable
    private SmashSet.Conflict conflict;

    public ReportGameMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;


        long user1 = getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = getTwoUsersChoicesActionMenuSettings().user2();
        underlying = new ConfirmableSelectionActionMenu<>(new ConfirmableSelectionActionMenu.Settings.Builder<Long>()
                .setActionMenuSettings(getActionMenuSettings())
                .setStart(settings.start())
                .addUsers(user1, user2)
                .registerChoice(SelectOption.of(StringUtils.abbreviate(String.format("%s won", settings.user1Display()), LABEL_MAX_LENGTH), String.valueOf(user1)), user1)
                .registerChoice(SelectOption.of(StringUtils.abbreviate(String.format("%s won", settings.user2Display()), LABEL_MAX_LENGTH), String.valueOf(user2)), user2)
                .setOnOptionChoice((info, event) -> event.deferEdit().queue())
                .setOnConfirmation(this::onConfirmation)
                .setOnAllConfirmation(this::onAllConfirmation)
                .setOnTimeout(this::onTimeout)
                .build());
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        underlying.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        underlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
        underlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        underlying.displayDeferredReplying(hook);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return underlying.getComponents();
    }

    @Override
    public void start(@Nonnull Message message) {
        underlying.start(message);
    }

    private synchronized void onConfirmation(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmationInfo info, @Nonnull ButtonInteractionEvent event) {
        settings.onChoice().accept(new ReportGameChoiceInfo(info.getUser(), info.getUserSelection(), info), event);

        if (!info.isAllConfirmed())
            event.reply("I have noted your choice.").setEphemeral(true).queue();
    }

    @Nonnull
    private synchronized MenuAction onAllConfirmation(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmationInfo info, @Nonnull ButtonInteractionEvent event) {
        long user1 = getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = getTwoUsersChoicesActionMenuSettings().user2();

        long user1ReportedWinner = info.getCurrentSelections().get(user1).getT();
        long user2ReportedWinner = info.getCurrentSelections().get(user2).getT();

        // Both have reported the winner
        if (user1ReportedWinner == user2ReportedWinner) {
            if (conflict != null) {
                SmashSet.Player wrongfulUser;
                if (conflict.isBothClaimedWin()) {
                    wrongfulUser = user1ReportedWinner == user2 ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
                } else {
                    wrongfulUser = user1ReportedWinner == user1 ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
                }

                conflict.setResolution(new SmashSet.ConflictResolution(wrongfulUser, null));
            }

            settings.onResult().accept(new ReportGameResult(info), event);

            return MenuAction.CANCEL;
        }

        // Conflict

        // We don't really need to do anything if there was a conflict before (the choice hasn't changed)
        if (conflict != null) return MenuAction.CONTINUE;

        conflict = new SmashSet.Conflict(user1ReportedWinner == user1);
        settings.onConflict().accept(new ReportGameConflict(info), event);

        event.editMessage(settings.conflictMessageProvider().apply(new ReportGameConflict(info), event))
                .setActionRows(event.getMessage().getActionRows())
                .queue();

        return MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmationInfoTimeoutEvent event) {
        settings.onTimeout().accept(new ReportGameTimeoutEvent());
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return underlying.getJDA();
    }

    @Override
    public long getMessageId() {
        return underlying.getMessageId();
    }

    @Override
    public long getChannelId() {
        return underlying.getChannelId();
    }

    @Nonnull
    public Settings getReportGameMenuSettings() {
        return settings;
    }

    private abstract class ReportGameMenuStateInfo extends TwoUsersMenuStateInfo {
        @Nonnull
        public Settings getReportGameMenuSettings() {
            return ReportGameMenu.this.getReportGameMenuSettings();
        }

        @Nullable
        public SmashSet.Conflict getConflict() {
            return conflict;
        }
    }

    private abstract class ContextReportGameMenuStateInfo extends ReportGameMenuStateInfo {
        @Nonnull
        private final ConfirmableSelectionActionMenu<Long>.ConfirmableSelectionInfo info;

        protected ContextReportGameMenuStateInfo(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmableSelectionInfo info) {
            this.info = info;
        }

        @Nonnull
        public Settings getReportGameMenuSettings() {
            return ReportGameMenu.this.getReportGameMenuSettings();
        }

        @Nullable
        public Long getUser1ReportedWinner() {
            return info.getCurrentSelections().get(settings.twoUsersChoicesActionMenuSettings().user1()).getT();
        }

        @Nullable
        public Long getUser2ReportedWinner() {
            return info.getCurrentSelections().get(settings.twoUsersChoicesActionMenuSettings().user2()).getT();
        }

        @Nullable
        public SmashSet.Conflict getConflict() {
            return conflict;
        }
    }

    public class ReportGameChoiceInfo extends ContextReportGameMenuStateInfo {
        private final long reportingUser;
        private final long reportedWinner;

        public ReportGameChoiceInfo(long reportingUser, long reportedWinner, @Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmableSelectionInfo info) {
            super(info);
            this.reportingUser = reportingUser;
            this.reportedWinner = reportedWinner;
        }

        public long getReportingUser() {
            return reportingUser;
        }

        public long getReportedWinner() {
            return reportedWinner;
        }
    }

    public class ReportGameConflict extends ContextReportGameMenuStateInfo {
        public ReportGameConflict(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmableSelectionInfo info) {
            super(info);
        }

        @Nonnull
        public Long getUser1ReportedWinner() {
            // Will not be null in conflict
            //noinspection ConstantConditions
            return super.getUser1ReportedWinner();
        }

        @Nonnull
        public Long getUser2ReportedWinner() {
            // Will not be null in conflict
            //noinspection ConstantConditions
            return super.getUser2ReportedWinner();
        }

        @Nonnull
        public SmashSet.Conflict getConflict() {
            // Will not be null in conflict
            //noinspection ConstantConditions
            return super.getConflict();
        }
    }

    public class ReportGameResult extends ContextReportGameMenuStateInfo {
        @Nonnull
        private final ConfirmableSelectionActionMenu<Long>.ConfirmationInfo confirmation;

        public ReportGameResult(@Nonnull ConfirmableSelectionActionMenu<Long>.ConfirmationInfo confirmation) {
            super(confirmation);
            this.confirmation = confirmation;
        }

        public long getWinner() {
            return confirmation.getUserSelection();
        }

        public long getLoser() {
            long user1 = getTwoUsersChoicesActionMenuSettings().user1();
            long user2 = getTwoUsersChoicesActionMenuSettings().user2();
            return getWinner() == user1 ? user2 : user1;
        }
    }

    public class ReportGameTimeoutEvent extends ReportGameMenuStateInfo {
    }

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull String user1Display, @Nonnull String user2Display,
                           @Nonnull BiFunction<ReportGameConflict, ButtonInteractionEvent, Message> conflictMessageProvider,
                           @Nonnull BiConsumer<ReportGameChoiceInfo, ButtonInteractionEvent> onChoice,
                           @Nonnull BiConsumer<ReportGameConflict, ButtonInteractionEvent> onConflict,
                           @Nonnull BiConsumer<ReportGameResult, ButtonInteractionEvent> onResult,
                           @Nonnull Message start,
                           @Nonnull Consumer<ReportGameTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiFunction<ReportGameConflict, ButtonInteractionEvent, Message> DEFAULT_CONFLICT_MESSAGE_PROVIDER = (conflict, e) -> {
            long user1 = conflict.getTwoUsersChoicesActionMenuSettings().user1();
            long user2 = conflict.getTwoUsersChoicesActionMenuSettings().user2();
            return new MessageBuilder(String.format("You reported different winners. %s reported %s, and %s reported %s as the winner. " +
                            "One of you can now either change your choice or you can call a moderator to sort this out.",
                    MiscUtil.mentionUser(user1),
                    MiscUtil.mentionUser(conflict.getUser1ReportedWinner()),
                    MiscUtil.mentionUser(user2),
                    MiscUtil.mentionUser(conflict.getUser2ReportedWinner())))
                    .mentionUsers(user1, user2)
                    .build();
        };
        @Nonnull
        public static final BiConsumer<ReportGameChoiceInfo, ButtonInteractionEvent> DEFAULT_ON_CHOICE = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<ReportGameConflict, ButtonInteractionEvent> DEFAULT_ON_CONFLICT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<ReportGameResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<ReportGameTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;
            @Nullable
            private String user1Display;
            @Nullable
            private String user2Display;
            @Nonnull
            private BiFunction<ReportGameConflict, ButtonInteractionEvent, Message> conflictMessageProvider = DEFAULT_CONFLICT_MESSAGE_PROVIDER;
            @Nonnull
            private BiConsumer<ReportGameChoiceInfo, ButtonInteractionEvent> onChoice = DEFAULT_ON_CHOICE;
            @Nonnull
            private BiConsumer<ReportGameConflict, ButtonInteractionEvent> onConflict = DEFAULT_ON_CONFLICT;
            @Nonnull
            private BiConsumer<ReportGameResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;
            @Nullable
            private Message start;
            @Nonnull
            private Consumer<ReportGameTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setTwoUsersChoicesActionMenuSettings(@Nullable TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setUsersDisplay(@Nonnull String user1Display, @Nonnull String user2Display) {
                this.user1Display = user1Display;
                this.user2Display = user2Display;
                return this;
            }

            @Nonnull
            public Builder setConflictMessageProvider(@Nonnull BiFunction<ReportGameConflict, ButtonInteractionEvent, Message> conflictMessageProvider) {
                this.conflictMessageProvider = conflictMessageProvider;
                return this;
            }

            @Nonnull
            public Builder setOnChoice(@Nonnull BiConsumer<ReportGameChoiceInfo, ButtonInteractionEvent> onChoice) {
                this.onChoice = onChoice;
                return this;
            }

            @Nonnull
            public Builder setOnConflict(@Nonnull BiConsumer<ReportGameConflict, ButtonInteractionEvent> onConflict) {
                this.onConflict = onConflict;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<ReportGameResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Builder setStart(@Nullable Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<ReportGameTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMEnuSettings must be set");
                if (user1Display == null || user2Display == null)
                    throw new IllegalStateException("UsersDisplay must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, user1Display, user2Display, conflictMessageProvider, onChoice, onConflict, onResult, start, onTimeout);
            }
        }
    }
}
