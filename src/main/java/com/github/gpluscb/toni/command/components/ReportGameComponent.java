package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReportGameComponent {
    @Nonnull
    private final EventWaiter waiter;

    public ReportGameComponent(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Nonnull
    public CompletableFuture<ReportGameResult> attachReportGame(@Nonnull Message message, long user1, long user2) {
        PairNonnull<CompletableFuture<ReportGameResult>, ButtonActionMenu> initPair = initReportGame(message, user1, user2);

        CompletableFuture<ReportGameResult> reportGameResult = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.display(message);

        return reportGameResult;
    }

    private PairNonnull<CompletableFuture<ReportGameResult>, ButtonActionMenu> initReportGame(@Nonnull Message message, long user1, long user2) {
        CompletableFuture<ReportGameResult> reportGameOutcome = new CompletableFuture<>();

        ReportGameComponent.ReportGameHandler handler = new ReportGameComponent.ReportGameHandler(user1, user2, reportGameOutcome);

        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(user1, user2)
                .setStart(message)
                .registerButton(Button.secondary("user1", Emoji.fromUnicode(Constants.ROCK)), handler::user1Button)
                .registerButton(Button.secondary("user2", Emoji.fromUnicode(Constants.PAPER)), handler::user2Button)
                .setTimeout(20, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout)
                .build();

        return new PairNonnull<>(reportGameOutcome, menu);
    }

    private static class ReportGameHandler {
        private final long user1;
        private final long user2;
        @Nonnull
        private final CompletableFuture<ReportGameResult> reportGameFuture;

        @Nullable
        private Long user1ReportedWinner;
        @Nullable
        private Long user2ReportedWinner;

        private ReportGameHandler(long user1, long user2, @Nonnull CompletableFuture<ReportGameResult> reportGameFuture) {
            this.user1 = user1;
            this.user2 = user2;
            this.reportGameFuture = reportGameFuture;
            user1ReportedWinner = null;
            user2ReportedWinner = null;
        }

        @Nonnull
        public synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> user1Button(@Nonnull ButtonClickEvent e) {
            return onButton(e, user1);
        }

        @Nonnull
        public synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> user2Button(@Nonnull ButtonClickEvent e) {
            return onButton(e, user2);
        }

        private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> onButton(@Nonnull ButtonClickEvent e, long votedFor) {
            long buttonPresser = e.getUser().getIdLong();
            boolean updatedChoice;

            if (buttonPresser == user1) {
                updatedChoice = user1ReportedWinner != null;
                user1ReportedWinner = votedFor;
            } else {
                updatedChoice = user2ReportedWinner != null;
                user2ReportedWinner = votedFor;
            }

            if (user1ReportedWinner != null && user2ReportedWinner != null) {
                if (user1ReportedWinner.equals(user2ReportedWinner)) {
                    reportGameFuture.complete(new ReportGameResult(user1ReportedWinner));
                    return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
                }

                e.reply("How do we actually handle conflicts lol??").queue(); // TODO
                return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
            }

            e.reply(String.format("I have %s your choice.", updatedChoice ? "updated" : "noted")).setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        public synchronized void timeout(@Nullable MessageChannel channel, long messageId) {
            ReportGameTimeoutException timeout = new ReportGameTimeoutException(user1ReportedWinner, user2ReportedWinner, channel, messageId);
            reportGameFuture.completeExceptionally(timeout);
        }
    }

    public static class ReportGameResult {
        private final long winner;
        // TODO: were there conflicts?

        public ReportGameResult(long winner) {
            this.winner = winner;
        }

        public long getWinner() {
            return winner;
        }
    }

    public static class ReportGameTimeoutException extends Exception {
        @Nullable
        private final Long user1ReportedWinner;
        @Nullable
        private final Long user2ReportedWinner;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public ReportGameTimeoutException(@Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ReportGameTimeoutException(String message, @Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            super(message);
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ReportGameTimeoutException(String message, Throwable cause, @Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            super(message, cause);
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ReportGameTimeoutException(Throwable cause, @Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            super(cause);
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ReportGameTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, @Nullable Long user1ReportedWinner, @Nullable Long user2ReportedWinner, @Nullable MessageChannel channel, long messageId) {
            super(message, cause, enableSuppression, writableStackTrace);
            this.user1ReportedWinner = user1ReportedWinner;
            this.user2ReportedWinner = user2ReportedWinner;
            this.channel = channel;
            this.messageId = messageId;
        }
    }
}
