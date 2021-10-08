package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
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
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class ReportGameMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Button modButton;
    @Nonnull
    private final BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice;
    @Nonnull
    private final BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict;
    @Nonnull
    private final BiConsumer<ReportGameResult, ButtonClickEvent> onResult;
    @Nonnull
    private final Consumer<ReportGameTimeoutEvent> onTimeout;
    @Nonnull
    private final ButtonActionMenu underlying;

    @Nullable
    private Long user1ReportedWinner;
    @Nullable
    private Long user2ReportedWinner;

    public ReportGameMenu(@Nonnull EventWaiter waiter, long user1, long user2, @Nonnull String user1Display, @Nonnull String user2Display, long timeout, @Nonnull TimeUnit unit, @Nonnull BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice, @Nonnull BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict, @Nonnull BiConsumer<ReportGameResult, ButtonClickEvent> onResult, @Nonnull Message start, @Nonnull Consumer<ReportGameTimeoutEvent> onTimeout) {
        super(waiter, user1, user2, timeout, unit);

        this.onChoice = onChoice;
        this.onConflict = onConflict;
        this.onResult = onResult;
        this.onTimeout = onTimeout;
        modButton = Button.danger("mod", "Call Moderator");

        underlying = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .setStart(start)
                .addUsers(user1, user2)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .registerButton(Button.secondary("user1", StringUtils.abbreviate(user1Display, LABEL_MAX_LENGTH)), e -> onChoice(user1, e))
                .registerButton(Button.secondary("user2", StringUtils.abbreviate(user2Display, LABEL_MAX_LENGTH)), e -> onChoice(user2, e))
                .registerButton(modButton, this::onCallMod, false)
                // Do these get captured too early??
                .setTimeoutAction(this::onTimeout)
                .build();
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
    private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> onChoice(long reportedWinner, @Nonnull ButtonClickEvent e) {
        long reportingUser = e.getUser().getIdLong();
        boolean updatedChoice;

        long user1 = getUser1();
        long user2 = getUser2();

        if (reportingUser == user1) {
            updatedChoice = user1ReportedWinner != null;
            user1ReportedWinner = reportedWinner;
        } else {
            updatedChoice = user2ReportedWinner != null;
            user2ReportedWinner = reportedWinner;
        }

        onChoice.accept(new ReportGameChoiceInfo(reportingUser, reportedWinner, updatedChoice), e);

        if (user1ReportedWinner == null || user2ReportedWinner == null) {
            // Only one has reported
            e.reply(String.format("I have %s your choice.", updatedChoice ? "updated" : "noted")).setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        // Both have reported the winner
        if (user1ReportedWinner.equals(user2ReportedWinner)) {
            onResult.accept(new ReportGameResult(), e);
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
        }

        // Conflict
        onConflict.accept(new ReportGameConflict(), e);

        Message message = e.getMessage();
        List<ActionRow> actionRows = MiscUtil.splitList(
                Stream.concat(message.getButtons().stream(), Stream.of(modButton)).collect(Collectors.toList()), Component.Type.BUTTON.getMaxPerRow()
        ).stream().map(ActionRow::of).collect(Collectors.toList());

        Message newMessage = new MessageBuilder(
                String.format("You reported different winners. %s reported %s and %s reported %s as the winner. " +
                                "One of you can now either change your choice or you can call a moderator to sort this out.",
                        MiscUtil.mentionUser(user1),
                        MiscUtil.mentionUser(user1ReportedWinner),
                        MiscUtil.mentionUser(user2),
                        MiscUtil.mentionUser(user2ReportedWinner))
        )
                .setActionRows(actionRows)
                .build();

        return OneOfTwo.ofT(newMessage);
    }

    @Nonnull
    private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> onCallMod(@Nonnull ButtonClickEvent e) {
        // TODO:
        return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        onTimeout.accept(new ReportGameTimeoutEvent());
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

    private abstract class ReportGameMenuStateInfo extends TwoUsersMenuStateInfo {
        @Nullable
        public Long getUser1ReportedWinner() {
            return user1ReportedWinner;
        }

        @Nullable
        public Long getUser2ReportedWinner() {
            return user2ReportedWinner;
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
    }

    public class ReportGameResult extends ReportGameMenuStateInfo {
        // TODO: were there conflicts?
        public long getWinner() {
            // Will not be null here
            //noinspection ConstantConditions
            return user1ReportedWinner;
        }
    }

    public class ReportGameTimeoutEvent extends ReportGameMenuStateInfo {
        // TODO: Already conflicted?
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, ReportGameMenu> {
        @Nullable
        private String user1Display;
        @Nullable
        private String user2Display;
        @Nonnull
        private BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice;
        @Nonnull
        private BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict;
        @Nonnull
        private BiConsumer<ReportGameResult, ButtonClickEvent> onResult;
        @Nullable
        private Message start;
        @Nonnull
        private Consumer<ReportGameTimeoutEvent> onTimeout;

        public Builder() {
            super(Builder.class);

            onChoice = (info, e) -> {
            };
            onConflict = (conflict, e) -> {
            };
            onResult = (result, e) -> {
            };
            onTimeout = timeout -> {
            };
        }

        @Nonnull
        public Builder setUsersDisplay(@Nonnull String user1Display, @Nonnull String user2Display) {
            this.user1Display = user1Display;
            this.user2Display = user2Display;
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

        @Nullable
        public String getUser1Display() {
            return user1Display;
        }

        @Nullable
        public String getUser2Display() {
            return user2Display;
        }

        @Nonnull
        public BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> getOnChoice() {
            return onChoice;
        }

        @Nonnull
        public BiConsumer<ReportGameConflict, ButtonClickEvent> getOnConflict() {
            return onConflict;
        }

        @Nonnull
        public BiConsumer<ReportGameResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        @Nonnull
        public Consumer<ReportGameTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        @Override
        public ReportGameMenu build() {
            preBuild();

            if (user1Display == null || user2Display == null)
                throw new IllegalStateException("UsersDisplay must be set");
            if (start == null) throw new IllegalStateException("Start must be set");

            // preBuild insures nonnullability
            //noinspection ConstantConditions
            return new ReportGameMenu(getWaiter(), getUser1(), getUser2(), user1Display, user2Display, getTimeout(), getUnit(), onChoice, onConflict, onResult, start, onTimeout);
        }
    }
}
