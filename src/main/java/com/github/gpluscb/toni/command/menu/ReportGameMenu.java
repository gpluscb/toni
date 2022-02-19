package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.SmashSet;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class ReportGameMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final Button modButton;
    @Nonnull
    private final ButtonActionMenu underlying;

    @Nullable
    private Long user1ReportedWinner;
    @Nullable
    private Long user2ReportedWinner;
    @Nullable
    private SmashSet.Conflict conflict;

    public ReportGameMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;

        modButton = Button.danger("mod", "Call Moderator");

        long user1 = getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = getTwoUsersChoicesActionMenuSettings().user2();
        underlying = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .setStart(settings.start())
                .addUsers(user1, user2)
                .setDeletionButton(null)
                .registerButton(Button.primary("user1", StringUtils.abbreviate(String.format("%s won", settings.user1Display()), LABEL_MAX_LENGTH)), e -> onChoice(user1, e))
                .registerButton(Button.primary("user2", StringUtils.abbreviate(String.format("%s won", settings.user2Display()), LABEL_MAX_LENGTH)), e -> onChoice(user2, e))
                .registerButton(modButton, this::onCallMod, false)
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
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        underlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        underlying.displayDeferredReplying(hook);
    }

    @Nonnull
    private synchronized MenuAction onChoice(long reportedWinner, @Nonnull ButtonClickEvent e) {
        long reportingUser = e.getUser().getIdLong();
        boolean updatedChoice;

        long user1 = getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = getTwoUsersChoicesActionMenuSettings().user2();

        Long previousUser1ReportedWinner = user1ReportedWinner;
        Long previousUser2ReportedWinner = user2ReportedWinner;

        if (reportingUser == user1) {
            updatedChoice = user1ReportedWinner != null;
            user1ReportedWinner = reportedWinner;
        } else {
            updatedChoice = user2ReportedWinner != null;
            user2ReportedWinner = reportedWinner;
        }

        settings.onChoice().accept(new ReportGameChoiceInfo(reportingUser, reportedWinner, updatedChoice), e);

        if (user1ReportedWinner == null || user2ReportedWinner == null) {
            // Only one has reported
            e.reply(String.format("I have %s your choice.", updatedChoice ? "updated" : "noted")).setEphemeral(true).queue();

            return MenuAction.CONTINUE;
        }

        // Both have reported the winner
        if (user1ReportedWinner.equals(user2ReportedWinner)) {
            if (conflict != null) {
                SmashSet.Player wrongfulUser;
                if (conflict.isBothClaimedWin()) {
                    wrongfulUser = user1ReportedWinner == user2 ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
                } else {
                    wrongfulUser = user1ReportedWinner == user1 ? SmashSet.Player.PLAYER1 : SmashSet.Player.PLAYER2;
                }

                conflict.setResolution(new SmashSet.ConflictResolution(wrongfulUser, null));
            }

            settings.onResult().accept(new ReportGameResult(), e);

            return MenuAction.CANCEL;
        }

        // Conflict

        // We only need to update ActionRows if a choice was *changed*
        // If we try to update the ActionRows otherwise it'll error
        // We also can't update the message with a full Message because of that reason
        // Tho it'd be possible with a MessageBuilder
        // Either way the conflict hasn't changed, so we don't update the message.
        if ((reportingUser == user1 && user1ReportedWinner.equals(previousUser1ReportedWinner)) || (reportingUser == user2 && user2ReportedWinner.equals(previousUser2ReportedWinner))) {
            e.reply("You have selected the same user you have already reported as the winner.").setEphemeral(true).queue();
            return MenuAction.CONTINUE;
        }

        conflict = new SmashSet.Conflict(user1ReportedWinner == user1);
        settings.onConflict().accept(new ReportGameConflict(), e);

        List<ActionRow> actionRows = MiscUtil.splitList(
                Stream.concat(e.getMessage().getButtons().stream(), Stream.of(modButton)).toList(), Component.Type.BUTTON.getMaxPerRow()
        ).stream().map(ActionRow::of).toList();

        e.editMessage(settings.conflictMessageProvider().apply(new ReportGameConflict(), e))
                .setActionRows(actionRows)
                .queue();

        return MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized MenuAction onCallMod(@Nonnull ButtonClickEvent e) {
        // TODO:
        return MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
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
        public Long getUser1ReportedWinner() {
            return user1ReportedWinner;
        }

        @Nullable
        public Long getUser2ReportedWinner() {
            return user2ReportedWinner;
        }

        @Nullable
        public SmashSet.Conflict getConflict() {
            return conflict;
        }
    }

    public class ReportGameChoiceInfo extends ReportGameMenuStateInfo {
        private final long reportingUser;
        private final long reportedWinner;
        private final boolean updatedChoice;

        public ReportGameChoiceInfo(long reportingUser, long reportedWinner, boolean updatedChoice) {
            this.reportingUser = reportingUser;
            this.reportedWinner = reportedWinner;
            this.updatedChoice = updatedChoice;
        }

        public long getReportingUser() {
            return reportingUser;
        }

        public long getReportedWinner() {
            return reportedWinner;
        }

        public boolean isUpdatedChoice() {
            return updatedChoice;
        }
    }

    public class ReportGameConflict extends ReportGameMenuStateInfo {
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

    public class ReportGameResult extends ReportGameMenuStateInfo {
        public long getWinner() {
            // Will not be null here
            //noinspection ConstantConditions
            return user1ReportedWinner;
        }

        public long getLoser() {
            long user1 = getTwoUsersChoicesActionMenuSettings().user1();
            long user2 = getTwoUsersChoicesActionMenuSettings().user2();
            return getWinner() == user1 ? user2 : user1;
        }
    }

    public class ReportGameTimeoutEvent extends ReportGameMenuStateInfo {
        // TODO: Already conflicted?
    }

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull String user1Display, @Nonnull String user2Display,
                           @Nonnull BiFunction<ReportGameConflict, ButtonClickEvent, Message> conflictMessageProvider,
                           @Nonnull BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice,
                           @Nonnull BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict,
                           @Nonnull BiConsumer<ReportGameResult, ButtonClickEvent> onResult, @Nonnull Message start,
                           @Nonnull Consumer<ReportGameTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiFunction<ReportGameConflict, ButtonClickEvent, Message> DEFAULT_CONFLICT_MESSAGE_PROVIDER = (conflict, e) -> {
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
        public static final BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> DEFAULT_ON_CHOICE = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<ReportGameConflict, ButtonClickEvent> DEFAULT_ON_CONFLICT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<ReportGameResult, ButtonClickEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
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
            private BiFunction<ReportGameConflict, ButtonClickEvent, Message> conflictMessageProvider = DEFAULT_CONFLICT_MESSAGE_PROVIDER;
            @Nonnull
            private BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice = DEFAULT_ON_CHOICE;
            @Nonnull
            private BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict = DEFAULT_ON_CONFLICT;
            @Nonnull
            private BiConsumer<ReportGameResult, ButtonClickEvent> onResult = DEFAULT_ON_RESULT;
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
            public Builder setConflictMessageProvider(@Nonnull BiFunction<ReportGameConflict, ButtonClickEvent, Message> conflictMessageProvider) {
                this.conflictMessageProvider = conflictMessageProvider;
                return this;
            }

            @Nonnull
            public Builder setOnChoice(@Nonnull BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice) {
                this.onChoice = onChoice;
                return this;
            }

            @Nonnull
            public Builder setOnConflict(@Nonnull BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict) {
                this.onConflict = onConflict;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<ReportGameResult, ButtonClickEvent> onResult) {
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
