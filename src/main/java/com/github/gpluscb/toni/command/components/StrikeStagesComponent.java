package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.SmashSet;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class StrikeStagesComponent {
    private static final Logger log = LogManager.getLogger(StrikeStagesComponent.class);

    @Nonnull
    private final EventWaiter waiter;

    public StrikeStagesComponent(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> sendStageStrikingReplying(@Nonnull Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2) {
        return sendStageStrikingReplying(reference, message, ruleset, striker1, striker2, null);
    }

    /**
     * If you pass a set, you swear not to touch it until we are finished
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> sendStageStrikingReplying(@Nonnull Message reference, @Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
        @SuppressWarnings("DuplicatedCode")
        PairNonnull<CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>>, ButtonActionMenu> initPair = initStrikeStages(message, ruleset, striker1, striker2, set);
        CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> stageStrikingResult = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.displayReplying(reference);

        return stageStrikingResult;
    }

    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2) {
        return attachStageStriking(message, ruleset, striker1, striker2, null);
    }

    /**
     * If you pass a set, you swear not to touch it until we are finished
     */
    @Nonnull
    public CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
        PairNonnull<CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>>, ButtonActionMenu> initPair = initStrikeStages(message, ruleset, striker1, striker2, set);
        CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> stageStrikingResult = initPair.getT();
        ButtonActionMenu menu = initPair.getU();

        menu.display(message);

        return stageStrikingResult;
    }

    @Nonnull
    private PairNonnull<CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>>, ButtonActionMenu> initStrikeStages(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
        CompletableFuture<PairNonnull<List<Set<Integer>>, ButtonClickEvent>> stageStrikingResult = new CompletableFuture<>();

        StageStrikingHandler handler = new StageStrikingHandler(stageStrikingResult, ruleset, striker1, striker2, set);

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

        return new PairNonnull<>(stageStrikingResult, menu);
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
            // TODO: Deactivate
            result.completeExceptionally(timeout);
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
