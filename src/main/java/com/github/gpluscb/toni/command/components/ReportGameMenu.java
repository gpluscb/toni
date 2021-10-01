package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class ReportGameMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final BiConsumer<ReportGameChoiceInfo, ButtonClickEvent> onChoice;
    @Nonnull
    private final BiConsumer<ReportGameConflict, ButtonClickEvent> onConflict;
    @Nonnull
    private final BiConsumer<ReportGameResult, ButtonClickEvent> onResult;
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

        underlying = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .setStart(start)
                .addUsers(user1, user2)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .registerButton(Button.secondary("user1", StringUtils.abbreviate(user1Display, LABEL_MAX_LENGTH)),e -> onChoice(user1, e))
                .registerButton(Button.secondary("user2", StringUtils.abbreviate(user2Display, LABEL_MAX_LENGTH)), e -> onChoice(user2, e))
                .setTimeoutAction((channel, messageId) -> onTimeout.accept(new ReportGameTimeoutEvent(user1, user2, user1ReportedWinner, user2ReportedWinner, channel, messageId)))
                .build();
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void display(@Nonnull Message message) {
        underlying.display(message);
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

        onChoice.accept(new ReportGameChoiceInfo(user1, user2, reportingUser, reportedWinner, updatedChoice), e);

        if (user1ReportedWinner != null && user2ReportedWinner != null) {
            if (user1ReportedWinner.equals(user2ReportedWinner)) {
                onResult.accept(new ReportGameResult(user1, user2, user1ReportedWinner), e);
                return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
            }

            onConflict.accept(new ReportGameConflict(user1, user2, user1ReportedWinner), e);
            e.reply("How do we actually handle conflicts lol??").queue(); // TODO
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        e.reply(String.format("I have %s your choice.", updatedChoice ? "updated" : "noted")).setEphemeral(true).queue();
        return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
    }

    public static class ReportGameChoiceInfo {
        private final long user1;
        private final long user2;
        private final long reportingUser;
        private final long reportedWinner;
        private final boolean updatedChoice;

        public ReportGameChoiceInfo(long user1, long user2, long reportingUser, long reportedWinner, boolean updatedChoice) {
            this.user1 = user1;
            this.user2 = user2;
            this.reportingUser = reportingUser;
            this.reportedWinner = reportedWinner;
            this.updatedChoice = updatedChoice;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
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

    public static class ReportGameConflict {
        private final long user1;
        private final long user2;
        private final long user1ReportedWinner;

        public ReportGameConflict(long user1, long user2, long user1ReportedWinner) {
            this.user1 = user1;
            this.user2 = user2;
            this.user1ReportedWinner = user1ReportedWinner;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
        }

        public long getUser1ReportedWinner() {
            return user1ReportedWinner;
        }

        public long getUser2ReportedWinner() {
            return user1ReportedWinner == user1 ? user2 : user1;
        }
    }

    public static class ReportGameResult {
        private final long user1;
        private final long user2;
        private final long winner;
        // TODO: were there conflicts?

        public ReportGameResult(long user1, long user2, long winner) {
            this.user1 = user1;
            this.user2 = user2;
            this.winner = winner;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
        }

        public long getWinner() {
            return winner;
        }
    }

    public static class ReportGameTimeoutEvent {
        // TODO: Already conflicted?
        private final long user1;
        private final long user2;
        @Nullable
        private final Long user1ReportedWinner;
        @Nullable
        private final Long user2ReportedWinner;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public ReportGameTimeoutEvent(long user1, long user2, @Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            this.user1 = user1;
            this.user2 = user2;
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
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
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
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
