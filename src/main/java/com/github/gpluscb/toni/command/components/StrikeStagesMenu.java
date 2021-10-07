package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class StrikeStagesMenu extends TwoUsersChoicesActionMenu {
    private static final Logger log = LogManager.getLogger(StrikeStagesMenu.class);

    @Nonnull
    private final BiConsumer<StrikeInfo, ButtonClickEvent> onStrike;
    @Nonnull
    private final BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes;
    @Nonnull
    private final BiConsumer<StrikeResult, ButtonClickEvent> onResult;
    @Nonnull
    private final Consumer<StrikeStagesTimeoutEvent> onTimeout;

    @Nonnull
    private final Ruleset ruleset;

    private long currentStriker;
    private int currentStrikeIdx;
    @Nonnull
    private Set<Integer> currentStrikes;
    @Nonnull
    private final List<Set<Integer>> strikes;

    @Nonnull
    private final ButtonActionMenu underlying;

    public StrikeStagesMenu(@Nonnull EventWaiter waiter, long timeout, @Nonnull TimeUnit unit, @Nonnull BiConsumer<StrikeInfo, ButtonClickEvent> onStrike, BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes, @Nonnull BiConsumer<StrikeResult, ButtonClickEvent> onResult, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nonnull Consumer<StrikeStagesTimeoutEvent> onTimeout) {
        super(waiter, striker1, striker2, timeout, unit);

        this.onStrike = onStrike;
        this.onUserStrikes = onUserStrikes;
        this.onResult = onResult;
        this.onTimeout = onTimeout;

        this.ruleset = ruleset;

        currentStriker = striker1;
        currentStrikeIdx = 0;
        currentStrikes = new LinkedHashSet<>();
        strikes = new ArrayList<>();
        strikes.add(currentStrikes);

        int[] starterStrikePattern = ruleset.getStarterStrikePattern();

        Message start;
        if (starterStrikePattern.length == 0) {
            // TODO: this doesn't really work yet. Maybe make underlying nullable and store start in a field?
            start = new MessageBuilder(String.format("Wow that's just very simple, there is only one stage in the ruleset. You're going to %s.",
                    ruleset.getStarters().get(0).getName())).build();
        } else {
            int firstStrikeAmount = starterStrikePattern[0];

            start = new MessageBuilder(String.format(
                    "Alright, time to strike stages. %s, you go first. Please strike %d stage%s from the list below.",
                    MiscUtil.mentionUser(striker1),
                    firstStrikeAmount,
                    firstStrikeAmount > 1 ? "s" : ""
            )).mentionUsers(striker1).build();
        }

        ButtonActionMenu.Builder underlyingBuilder = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .addUsers(striker1, striker2)
                .setStart(start)
                .setDeletionButton(null)
                .setTimeout(timeout, unit)
                .setTimeoutAction(this::onTimeout);

        for (Stage starter : ruleset.getStarters()) {
            int id = starter.getStageId();
            underlyingBuilder.registerButton(
                    Button.secondary(String.valueOf(id), StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)),
                    e -> handleStrike(e, id)
            );
        }

        underlying = underlyingBuilder.build();
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
    private OneOfTwo<Message, ButtonActionMenu.MenuAction> handleStrike(@Nonnull ButtonClickEvent e, int stageId) {
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
        onStrike.accept(new StrikeInfo(stageId), e);

        long striker1 = getUser1();
        long striker2 = getUser2();

        int[] starterStrikePattern = ruleset.getStarterStrikePattern();
        if (currentStrikes.size() == starterStrikePattern[currentStrikeIdx]) {
            onUserStrikes.accept(new UserStrikesInfo(currentStrikes), e);

            if (strikes.size() == starterStrikePattern.length) {
                onResult.accept(new StrikeResult(), e);

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

        List<Button> buttonsToAdd = ruleset.getStarters().stream()
                .map(starter -> Button.secondary(
                                String.valueOf(starter.getStageId()),
                                StringUtils.abbreviate(starter.getName(), LABEL_MAX_LENGTH)
                        ).withDisabled(strikes.stream().anyMatch(struckIds -> struckIds.contains(starter.getStageId())))
                ).collect(Collectors.toList());

        // Multiple ActionRows in case of > 5 buttons
        List<List<Button>> splitButtonsToAdd = MiscUtil.splitList(buttonsToAdd, Component.Type.BUTTON.getMaxPerRow());

        List<ActionRow> actionRows = splitButtonsToAdd.stream().map(ActionRow::of).collect(Collectors.toList());
        builder.setActionRows(actionRows);

        return OneOfTwo.ofT(builder.build());
    }

    private synchronized void onTimeout(@Nullable MessageChannel channel, long messageId) {
        onTimeout.accept(new StrikeStagesTimeoutEvent(channel, messageId));
    }

    private abstract class StrikeStagesInfo extends TwoUsersMenuStateInfo {
        public long getStriker1() {
            return getUser1();
        }

        public long getStriker2() {
            return getUser2();
        }

        @Nonnull
        public Ruleset getRuleset() {
            return ruleset;
        }

        public long getCurrentStriker() {
            return currentStriker;
        }

        public int getCurrentStrikeIdx() {
            return currentStrikeIdx;
        }

        @Nonnull
        public List<Set<Integer>> getStrikes() {
            return strikes;
        }
    }

    public class StrikeInfo extends StrikeStagesInfo {
        private final int struckStageId;

        public StrikeInfo(int struckStageId) {
            this.struckStageId = struckStageId;
        }

        public int getStruckStageId() {
            return struckStageId;
        }

        @Nonnull
        public Stage getStruckStage() {
            //noinspection OptionalGetWithoutIsPresent
            return ruleset.getStagesStream().filter(stage -> stage.getStageId() == struckStageId).findAny().get();
        }
    }

    public class UserStrikesInfo extends StrikeStagesInfo {
        @Nonnull
        private final Set<Integer> struckStagesIds;

        public UserStrikesInfo(@Nonnull Set<Integer> struckStagesIds) {
            this.struckStagesIds = struckStagesIds;
        }

        @Nonnull
        public Set<Integer> getStruckStagesIds() {
            return struckStagesIds;
        }

        @Nonnull
        public List<Stage> getStruckStages() {
            return ruleset.getStagesStream()
                    .filter(stage -> struckStagesIds.contains(stage.getStageId()))
                    .collect(Collectors.toList());
        }
    }

    public class StrikeResult extends StrikeStagesInfo {
        /**
         * This is only null in the case that the ruleset only has one stage
         */
        @Nullable
        public Stage getLeftStage() {
            return ruleset.getStagesStream()
                    .filter(stage ->
                            strikes.stream()
                                    .flatMap(Set::stream)
                                    .noneMatch(struckStageId -> stage.getStageId() == struckStageId))
                    .findAny()
                    .orElse(null);
        }
    }

    public class StrikeStagesTimeoutEvent extends StrikeStagesInfo implements TwoUsersMenuTimeoutEvent {
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public StrikeStagesTimeoutEvent(@Nullable MessageChannel channel, long messageId) {
            this.channel = channel;
            this.messageId = messageId;
        }

        @Override
        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        @Override
        public long getMessageId() {
            return messageId;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, StrikeStagesMenu> {
        @Nullable
        private Ruleset ruleset;
        @Nonnull
        private BiConsumer<StrikeInfo, ButtonClickEvent> onStrike;
        @Nonnull
        private BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes;
        @Nonnull
        private BiConsumer<StrikeResult, ButtonClickEvent> onResult;
        @Nonnull
        private Consumer<StrikeStagesTimeoutEvent> onTimeout;

        public Builder() {
            super(Builder.class);

            onStrike = (info, e) -> {
            };
            onUserStrikes = (info, e) -> {
            };
            onResult = (result, e) -> {
            };
            onTimeout = timeout -> {
            };
        }

        @Nonnull
        public Builder setRuleset(@Nonnull Ruleset ruleset) {
            this.ruleset = ruleset;
            return this;
        }

        @Nonnull
        public Builder setOnStrike(@Nonnull BiConsumer<StrikeInfo, ButtonClickEvent> onStrike) {
            this.onStrike = onStrike;
            return this;
        }

        @Nonnull
        public Builder setOnUserStrikes(@Nonnull BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes) {
            this.onUserStrikes = onUserStrikes;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<StrikeResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public Builder setOnTimeout(@Nonnull Consumer<StrikeStagesTimeoutEvent> onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        @Nullable
        public Ruleset getRuleset() {
            return ruleset;
        }

        @Nonnull
        public BiConsumer<StrikeInfo, ButtonClickEvent> getOnStrike() {
            return onStrike;
        }

        @Nonnull
        public BiConsumer<UserStrikesInfo, ButtonClickEvent> getOnUserStrikes() {
            return onUserStrikes;
        }

        @Nonnull
        public BiConsumer<StrikeResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<StrikeStagesTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        @Override
        public StrikeStagesMenu build() {
            preBuild();

            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

            // We know because of preBuild nonnulls are not violated
            //noinspection ConstantConditions
            return new StrikeStagesMenu(getWaiter(), getTimeout(), getUnit(), onStrike, onUserStrikes, onResult, ruleset, getUser1(), getUser2(), onTimeout);
        }
    }
}
