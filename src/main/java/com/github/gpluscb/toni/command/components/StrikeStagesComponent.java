package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.ButtonActionMenu;
import com.github.gpluscb.toni.util.MiscUtil;
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
    public CompletableFuture<List<Set<Integer>>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2) {
        return attachStageStriking(message, ruleset, striker1, striker2, null);
    }

    /**
     * If you pass a set, you swear not to touch it until we are finished
     */
    @Nonnull
    public CompletableFuture<List<Set<Integer>>> attachStageStriking(@Nonnull Message message, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
        CompletableFuture<List<Set<Integer>>> stageStrikingResult = new CompletableFuture<>();

        StageStrikingHandler handler = new StageStrikingHandler(stageStrikingResult, ruleset, striker1, striker2, set);

        ButtonActionMenu.Builder builder = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(striker1, striker2)
                .setStart(message)
                .setTimeout(3, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout);

        for (Stage starter : ruleset.getStarters()) {
            builder.registerButton(
                    Button.secondary(String.valueOf(starter.getStageId()), StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)),
                    e -> handler.handleStrike(e, starter.getStageId())
            );
        }

        ButtonActionMenu menu = builder.build();

        menu.display(message);

        return stageStrikingResult;
    }

    private static class StageStrikingHandler {
        @Nonnull
        private final CompletableFuture<List<Set<Integer>>> result;
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


        public StageStrikingHandler(@Nonnull CompletableFuture<List<Set<Integer>>> result, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nullable SmashSet.SetStarterStrikingState set) {
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
        }

        @Nullable
        public synchronized Message handleStrike(@Nonnull ButtonClickEvent e, int stageId) {
            // TODO: I dislike that users have no control over how these messages look
            e.deferEdit().queue();

            if (e.getUser().getIdLong() != currentStriker) {
                e.reply("It's not your turn to strike right now!").setEphemeral(true).queue();
                return null;
            }

            if (strikes.stream().flatMap(Collection::stream).anyMatch(id -> id == stageId)) {
                log.warn("Stage was double struck. Race condition or failure to set as disabled?");
                return new MessageBuilder("That stage has already been struck. Please strike a different one.").build();
            }

            currentStrikes.add(stageId);

            int[] starterStrikePattern = ruleset.getStarterStrikePattern();
            if (currentStrikes.size() == starterStrikePattern[currentStrikeIdx]) {
                // If we didn't mess up this should always be ok (except someone else messed with the set)
                if (set != null) set.strikeStages(currentStrikes);
                strikes.add(currentStrikes);

                if (strikes.size() == starterStrikePattern.length) {
                    result.complete(strikes);

                    return null; // TODO
                }

                currentStrikes = new HashSet<>();
                currentStrikeIdx++;
                currentStriker = currentStriker == striker1 ? striker2 : striker1; // Invert
            }

            MessageBuilder builder = new MessageBuilder();
            builder.appendFormat("%s, please strike %d stages from the list below.",
                    MiscUtil.mentionUser(currentStriker),
                    starterStrikePattern[currentStrikeIdx] - currentStrikes.size())
                    .mentionUsers(currentStriker);

            builder.setActionRows(ActionRow.of(
                    ruleset.getStarters().stream()
                            .map(starter -> Button.secondary(
                                    String.valueOf(starter.getStageId()),
                                    StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)
                                    ).withDisabled(strikes.stream().flatMap(Collection::stream).anyMatch(struckId -> starter.getStageId() == struckId))
                            ).collect(Collectors.toList())
            ));

            return builder.build();
        }

        public void timeout(@Nullable MessageChannel channel, long messageId) {
            // TODO
        }
    }
}
