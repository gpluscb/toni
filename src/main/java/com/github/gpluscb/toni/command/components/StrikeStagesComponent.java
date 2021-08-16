package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.*;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.SmashSet;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class StrikeStagesComponent {
    private static final Logger log = LogManager.getLogger(StrikeStagesComponent.class);

    @Nonnull
    private final RPSComponent rpsComponent;
    @Nonnull
    private final EventWaiter waiter;

    public StrikeStagesComponent(@Nonnull RPSComponent rpsComponent, @Nonnull EventWaiter waiter) {
        this.rpsComponent = rpsComponent;
        this.waiter = waiter;
    }

    /**
     * The future can also fail with the {@link com.github.gpluscb.toni.command.components.RPSComponent.RPSTimeoutException} or {@link ChooseFirstStrikerTimeoutException}.
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> sendStageStrikingReplying(@Nonnull Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS) {
        return sendStageStrikingReplying(reference, message, ruleset, striker1, striker2, doRPS, null);
    }

    /**
     * If you pass a set, you swear not to touch it until we are finished.
     * The future can also fail with the {@link com.github.gpluscb.toni.command.components.RPSComponent.RPSTimeoutException} or {@link ChooseFirstStrikerTimeoutException}.
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> sendStageStrikingReplying(@Nonnull Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS, @Nullable SmashSet.SetStarterStrikingState set) {
        return initStrikeStagesReplying(reference, message, ruleset, striker1, striker2, doRPS, set);
    }

    /**
     * The future can also fail with the {@link com.github.gpluscb.toni.command.components.RPSComponent.RPSTimeoutException} or {@link ChooseFirstStrikerTimeoutException}.
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS) {
        return attachStageStriking(message, ruleset, striker1, striker2, doRPS, null);
    }

    /**
     * If you pass a set, you swear not to touch it until we are finished.
     * The future can also fail with the {@link com.github.gpluscb.toni.command.components.RPSComponent.RPSTimeoutException} or {@link ChooseFirstStrikerTimeoutException}.
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS, @Nullable SmashSet.SetStarterStrikingState set) {
        return initStrikeStages(message, ruleset, striker1, striker2, doRPS, set);
    }

    @Nonnull
    private CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> initStrikeStages(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS, @Nullable SmashSet.SetStarterStrikingState set) {
        return initStrikeStagesHelper(null, message, ruleset, striker1, striker2, doRPS, set);
    }

    @Nonnull
    private CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> initStrikeStagesReplying(@Nonnull Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS, @Nullable SmashSet.SetStarterStrikingState set) {
        return initStrikeStagesHelper(reference, message, ruleset, striker1, striker2, doRPS, set);
    }

    @Nonnull
    private CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> initStrikeStagesHelper(@Nullable Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, boolean doRPS, @Nullable SmashSet.SetStarterStrikingState set) {
        // Message will be the message to edit, or null if not present
        CompletableFuture<Pair<Boolean, Message>> user1StrikesFirstFuture;
        if (doRPS) {
            CompletableFuture<PairNonnull<RPSComponent.RPSResult, ButtonClickEvent>> rpsResult;
            if (reference == null)
                rpsResult = rpsComponent.attachRPS(message, striker1, striker2);
            else
                rpsResult = rpsComponent.sendRPSReplying(reference, message, striker1, striker2);

            user1StrikesFirstFuture = rpsResult.thenCompose(pair -> evaluateRPSResult(striker1, striker2, pair.getT(), pair.getU()));
        } else {
            user1StrikesFirstFuture = CompletableFuture.completedFuture(new Pair<>(ThreadLocalRandom.current().nextBoolean(), reference == null ? message : null));
        }

        return user1StrikesFirstFuture.thenCompose(pair -> {
            boolean user1StrikesFirst = pair.getT();
            @Nullable Message toEdit = pair.getU();

            long firstStriker = user1StrikesFirst ? striker1 : striker2;
            long secondStriker = user1StrikesFirst ? striker2 : striker1;

            CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> stageStrikingResult = new CompletableFuture<>();

            StageStrikingHandler handler = new StageStrikingHandler(stageStrikingResult, ruleset, firstStriker, secondStriker, set);

            ButtonActionMenu.Builder builder = new ButtonActionMenu.Builder()
                    .setEventWaiter(waiter)
                    .setDeletionButton(null)
                    .addUsers(striker1, striker2)
                    .setStart(message)
                    .setTimeout(5, TimeUnit.MINUTES)
                    .setTimeoutAction(handler::timeout);

            for (Stage starter : ruleset.getStarters()) {
                builder.registerButton(
                        Button.secondary(String.valueOf(starter.getStageId()), StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)),
                        e -> handler.handleStrike(e, starter.getStageId())
                );
            }

            ButtonActionMenu menu = builder.build();

            if (reference == null || toEdit != null)
                menu.display(toEdit == null ? message : toEdit);
            else
                menu.displayReplying(reference);

            return stageStrikingResult;
        });
    }

    /**
     * Message will be the message to edit, or null if not present
     */
    @Nonnull
    private CompletableFuture<Pair<Boolean, Message>> evaluateRPSResult(long user1, long user2, @Nonnull RPSComponent.RPSResult result, @Nonnull ButtonClickEvent e) {
        long winner;

        switch (result.getWinner()) {
            case Tie:
                String choice = result.getChoiceA().getDisplayName();
                return e.editMessage(String.format("Both of you chose %s. So please try again.", choice))
                        .flatMap(InteractionHook::retrieveOriginal)
                        .submit()
                        .thenCompose(m -> rpsComponent.attachRPS(m, user1, user2))
                        .thenCompose(pair -> {
                            RPSComponent.RPSResult newResult = pair.getT();
                            ButtonClickEvent newE = pair.getU();
                            return evaluateRPSResult(user1, user2, newResult, newE);
                        });
            case A:
                winner = user1;
                break;
            case B:
                winner = user2;
                break;
            default:
                throw new IllegalStateException("Incomplete switch over Winner");
        }

        CompletableFuture<Pair<Boolean, Message>> user1StrikesFirstFuture = new CompletableFuture<>();

        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(winner)
                .setStart(new MessageBuilder(String.format(
                        "%s chose %s, and %s chose %s. So %s, you won the RPS. Will you strike first or second?",
                        MiscUtil.mentionUser(user1),
                        result.getChoiceA().getDisplayName(),
                        MiscUtil.mentionUser(user2),
                        result.getChoiceB().getDisplayName(),
                        MiscUtil.mentionUser(winner)
                )).build())
                .setTimeout(5, TimeUnit.MINUTES)
                .registerButton(Button.secondary("first", Emoji.fromUnicode(Constants.ONE)), event -> {
                    e.deferEdit().queue();
                    user1StrikesFirstFuture.complete(new Pair<>(winner == user1, e.getMessage()));
                    return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
                }).registerButton(Button.secondary("second", Emoji.fromUnicode(Constants.TWO)), event -> {
                    e.deferEdit().queue();
                    user1StrikesFirstFuture.complete(new Pair<>(winner != user1, e.getMessage()));
                    return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
                })
                .setTimeoutAction((channel, messageId) -> {
                    ChooseFirstStrikerTimeoutException exception = new ChooseFirstStrikerTimeoutException(winner, channel, messageId);
                    user1StrikesFirstFuture.completeExceptionally(exception);
                }).build();

        e.deferEdit().queue();
        //noinspection ConstantConditions Not ephemeral
        menu.display(e.getMessage());

        return user1StrikesFirstFuture;
    }

    private static class StageStrikingHandler {
        @Nonnull
        private final CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> result;
        @Nonnull
        private final Ruleset ruleset;
        private final long striker1;
        private final long striker2;
        @Nullable
        private final SmashSet.SetStarterStrikingState set;

        private long currentStriker;
        private int currentStrikeIdx;
        @Nonnull
        private Set<Integer> currentStrikes;
        @Nonnull
        private final List<Set<Integer>> strikes;

        public StageStrikingHandler(@Nonnull CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> result, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
            this.result = result;
            this.ruleset = ruleset;
            this.striker1 = striker1;
            this.striker2 = striker2;
            this.set = set;

            if (set != null && set.getSmashSet().getRuleset() != ruleset)
                throw new IllegalArgumentException("ruleset does not match ruleset of set");

            currentStriker = striker1;
            currentStrikeIdx = 0;
            currentStrikes = new LinkedHashSet<>();
            strikes = new ArrayList<>();
            strikes.add(currentStrikes);
        }

        @Nonnull
        public synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> handleStrike(@Nonnull ButtonClickEvent e, int stageId) {
            // TODO: I dislike that users of this class have no control over how these messages look
            if (e.getUser().getIdLong() != currentStriker) {
                e.reply("It's not your turn to strike right now!").setEphemeral(true).queue();
                return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
            }

            if (strikes.stream().anyMatch(struckStages -> struckStages.contains(stageId))) {
                e.deferEdit().queue();
                log.warn("Stage was double struck. Race condition or failure to set as disabled?");
                return OneOfTwo.ofT(new MessageBuilder("That stage has already been struck. Please strike a different one.").build());
            }

            currentStrikes.add(stageId);

            int[] starterStrikePattern = ruleset.getStarterStrikePattern();
            if (currentStrikes.size() == starterStrikePattern[currentStrikeIdx]) {
                // If we didn't mess up this should always be ok (except someone else messed with the set)
                if (set != null) set.strikeStages(currentStrikes);

                if (strikes.size() == starterStrikePattern.length) {
                    result.complete(new PairNonnull<>(strikes, e));

                    return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
                }

                currentStrikes = new HashSet<>();
                strikes.add(currentStrikes);
                currentStrikeIdx++;
                currentStriker = currentStriker == striker1 ? striker2 : striker1; // Invert
            }

            e.deferEdit().queue();
            int stagesToStrike = starterStrikePattern[currentStrikeIdx] - currentStrikes.size();
            MessageBuilder builder = new MessageBuilder();
            builder.appendFormat("%s, please strike %d stage%s from the list below.",
                            MiscUtil.mentionUser(currentStriker),
                            stagesToStrike,
                            stagesToStrike > 1 ? "s" : "")
                    .mentionUsers(currentStriker);

            builder.setActionRows(ActionRow.of(
                    ruleset.getStarters().stream()
                            .map(starter -> Button.secondary(
                                            String.valueOf(starter.getStageId()),
                                            StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)
                                    ).withDisabled(strikes.stream().anyMatch(struckIds -> struckIds.contains(starter.getStageId())))
                            ).collect(Collectors.toList())
            ));

            return OneOfTwo.ofT(builder.build());
        }

        public synchronized void timeout(@Nullable MessageChannel channel, long messageId) {
            StrikeStagesTimeoutException timeout = new StrikeStagesTimeoutException(strikes, currentStriker, channel, messageId);
            result.completeExceptionally(timeout);
        }
    }

    public static class ChooseFirstStrikerTimeoutException extends Exception {
        private final long user;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public ChooseFirstStrikerTimeoutException(long user, @Nullable MessageChannel channel, long messageId) {
            this.user = user;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ChooseFirstStrikerTimeoutException(String message, long user, @Nullable MessageChannel channel, long messageId) {
            super(message);
            this.user = user;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ChooseFirstStrikerTimeoutException(String message, Throwable cause, long user, @Nullable MessageChannel channel, long messageId) {
            super(message, cause);
            this.user = user;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ChooseFirstStrikerTimeoutException(Throwable cause, long user, @Nullable MessageChannel channel, long messageId) {
            super(cause);
            this.user = user;
            this.channel = channel;
            this.messageId = messageId;
        }

        public ChooseFirstStrikerTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, long user, @Nullable MessageChannel channel, long messageId) {
            super(message, cause, enableSuppression, writableStackTrace);
            this.user = user;
            this.channel = channel;
            this.messageId = messageId;
        }
    }

    public static class StrikeStagesTimeoutException extends Exception {
        @Nonnull
        private final List<Set<Integer>> strikesSoFar;
        private final long currentStriker;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public StrikeStagesTimeoutException(@Nonnull List<Set<Integer>> strikesSoFar, long currentStriker, @Nullable MessageChannel channel, long messageId) {
            this.strikesSoFar = strikesSoFar;
            this.currentStriker = currentStriker;
            this.channel = channel;
            this.messageId = messageId;
        }

        public StrikeStagesTimeoutException(String message, @Nonnull List<Set<Integer>> strikesSoFar, long currentStriker, @Nullable MessageChannel channel, long messageId) {
            super(message);
            this.strikesSoFar = strikesSoFar;
            this.currentStriker = currentStriker;
            this.channel = channel;
            this.messageId = messageId;
        }

        public StrikeStagesTimeoutException(String message, Throwable cause, @Nonnull List<Set<Integer>> strikesSoFar, long currentStriker, @Nullable MessageChannel channel, long messageId) {
            super(message, cause);
            this.strikesSoFar = strikesSoFar;
            this.currentStriker = currentStriker;
            this.channel = channel;
            this.messageId = messageId;
        }

        public StrikeStagesTimeoutException(Throwable cause, @Nonnull List<Set<Integer>> strikesSoFar, long currentStriker, @Nullable MessageChannel channel, long messageId) {
            super(cause);
            this.strikesSoFar = strikesSoFar;
            this.currentStriker = currentStriker;
            this.channel = channel;
            this.messageId = messageId;
        }

        public StrikeStagesTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, @Nonnull List<Set<Integer>> strikesSoFar, long currentStriker, @Nullable MessageChannel channel, long messageId) {
            super(message, cause, enableSuppression, writableStackTrace);
            this.strikesSoFar = strikesSoFar;
            this.currentStriker = currentStriker;
            this.channel = channel;
            this.messageId = messageId;
        }

        @Nonnull
        public List<Set<Integer>> getStrikesSoFar() {
            return strikesSoFar;
        }

        public long getCurrentStriker() {
            return currentStriker;
        }

        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
    }
}
