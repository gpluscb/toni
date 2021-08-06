package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.*;
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

public class RPSComponent {
    @Nonnull
    private final EventWaiter waiter;

    public RPSComponent(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    /**
     * It is the callers responsibility to edit the message (and remove the buttons if you want)
     * The message must already exist
     */
    @Nonnull
    public CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> attachRPS(@Nonnull Message message, long user1, long user2) {
        PairNonnull<CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>>, ButtonActionMenu> initPair = initRps(message, user1, user2);

        CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> rpsOutcome = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.display(message);

        return rpsOutcome;
    }

    /**
     * It is the callers responsibility to edit the message (and remove the buttons if you want)
     * The message should be one returned by MessageBuilder
     */
    @Nonnull
    public CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> sendRPSReplying(@Nonnull Message reference, @Nonnull Message message, long user1, long user2) {
        PairNonnull<CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>>, ButtonActionMenu> initPair = initRps(message, user1, user2);

        CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> rpsOutcome = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.displayReplying(reference);

        return rpsOutcome;
    }

    @Nonnull
    private PairNonnull<CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>>, ButtonActionMenu> initRps(@Nonnull Message message, long user1, long user2) {
        CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> rpsOutcome = new CompletableFuture<>();

        RPSHandler handler = new RPSHandler(user1, user2, rpsOutcome);

        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(user1, user2)
                .setStart(message)
                .registerButton(Button.secondary("rock", Emoji.fromUnicode(Constants.ROCK)), handler::rockButton)
                .registerButton(Button.secondary("paper", Emoji.fromUnicode(Constants.PAPER)), handler::paperButton)
                .registerButton(Button.secondary("scissors", Emoji.fromUnicode(Constants.SCISSORS)), handler::scissorsButton)
                .setTimeout(3, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout)
                .build();

        return new PairNonnull<>(rpsOutcome, menu);
    }

    private static class RPSHandler {
        @Nonnull
        private final CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> outcomeFuture;

        private boolean finished;
        private final long user1;
        private final long user2;
        @Nullable
        private RPS choice1;
        @Nullable
        private RPS choice2;

        private RPSHandler(long user1, long user2, @Nonnull CompletableFuture<PairNonnull<RPSResult, ButtonClickEvent>> outcomeFuture) {
            this.outcomeFuture = outcomeFuture;
            finished = false;
            this.user1 = user1;
            this.user2 = user2;
        }

        @Nullable
        public synchronized Message rockButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.ROCK);
            return null;
        }

        @Nullable
        public synchronized Message paperButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.PAPER);
            return null;
        }

        @Nullable
        public synchronized Message scissorsButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.SCISSORS);
            return null;
        }

        private synchronized void choose(@Nonnull ButtonClickEvent e, boolean isUser1, @Nonnull RPS choice) {
            if (finished) return;

            if ((isUser1 && choice1 != null) || (!isUser1 && choice2 != null)) {
                e.reply("You have already chosen, and you must learn to live with that choice!")
                        .setEphemeral(true).queue();
                return;
            }

            if (isUser1) choice1 = choice;
            else choice2 = choice;

            if (choice1 != null && choice2 != null) {
                RPSResult outcome = RPS.determineWinner(choice1, choice2);
                outcomeFuture.complete(new PairNonnull<>(outcome, e));

                finished = true;
            } else {
                e.reply("I have noted your choice...").setEphemeral(true).queue();
            }
        }

        // TODO: Fail the completablefuture
        public synchronized void timeout(@Nullable MessageChannel channel, long messageId) {
            if (channel == null) return;

            // TODO: Variable naming
            StringBuilder lazyIdiots = new StringBuilder();
            if (choice1 == null) {
                lazyIdiots.append(MiscUtil.mentionUser(user1));
                if (choice2 == null) lazyIdiots.append(" and ");
            }
            if (choice2 == null) lazyIdiots.append(MiscUtil.mentionUser(user2));

            channel.sendMessage(String.format("The three (3) minutes are done. Not all of you have given me your choice. Shame on you, %s!", lazyIdiots)).mentionUsers(user1, user2)
                    .queue();

            channel.retrieveMessageById(messageId).flatMap(m -> m.editMessage(m).setActionRows()).queue();
        }
    }

    public enum RPS {
        ROCK,
        PAPER,
        SCISSORS;

        @Nonnull
        public String getDisplayName() {
            switch (this) {
                case ROCK:
                    return Constants.ROCK + "(rock)";
                case PAPER:
                    return Constants.PAPER + "(paper)";
                case SCISSORS:
                    return Constants.SCISSORS + "(scissors)";
                default:
                    throw new IllegalStateException("Enum switch failed");
            }
        }

        @Nonnull
        public static RPSResult determineWinner(@Nonnull RPS a, @Nonnull RPS b) {
            if (a == b) return new RPSResult(RPSResult.Winner.Tie, a, b);
            RPSResult.Winner winner;
            switch (a) {
                case ROCK:
                    winner = b == PAPER ? RPSResult.Winner.B : RPSResult.Winner.A;
                    break;
                case PAPER:
                    winner = b == SCISSORS ? RPSResult.Winner.B : RPSResult.Winner.A;
                    break;
                case SCISSORS:
                    winner = b == ROCK ? RPSResult.Winner.B : RPSResult.Winner.A;
                    break;
                default:
                    throw new IllegalStateException("Not all RPS options covered");
            }

            return new RPSResult(winner, a, b);
        }
    }

    public static class RPSResult {
        public enum Winner {
            A,
            B,
            Tie,
        }

        @Nonnull
        private final Winner winner;
        @Nonnull
        private final RPS choiceA;
        @Nonnull
        private final RPS choiceB;

        public RPSResult(@Nonnull Winner winner, @Nonnull RPS choiceA, @Nonnull RPS choiceB) {
            this.winner = winner;
            this.choiceA = choiceA;
            this.choiceB = choiceB;
        }

        @Nonnull
        public Winner getWinner() {
            return winner;
        }

        @Nonnull
        public RPS getChoiceA() {
            return choiceA;
        }

        @Nonnull
        public RPS getChoiceB() {
            return choiceB;
        }
    }
}
