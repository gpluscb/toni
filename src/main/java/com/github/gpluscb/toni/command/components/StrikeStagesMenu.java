package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class StrikeStagesMenu extends TwoUsersChoicesActionMenu {
    private static final Logger log = LogManager.getLogger(StrikeStagesMenu.class);

    @Nonnull
    private final Function<UpcomingStrikeInfo, MessageBuilder> strikeMessageProducer;
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

    public StrikeStagesMenu(@Nonnull EventWaiter waiter, long timeout, @Nonnull TimeUnit unit, @Nonnull Function<UpcomingStrikeInfo, MessageBuilder> strikeMessageProducer, @Nonnull BiConsumer<StrikeInfo, ButtonClickEvent> onStrike, @Nonnull BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes, @Nonnull BiConsumer<StrikeResult, ButtonClickEvent> onResult, @Nonnull Ruleset ruleset, long striker1, long striker2, @Nonnull Consumer<StrikeStagesTimeoutEvent> onTimeout) {
        super(waiter, striker1, striker2, timeout, unit);

        this.strikeMessageProducer = strikeMessageProducer;
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

        Message start = strikeMessageProducer.apply(new UpcomingStrikeInfo()).build();

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
    private OneOfTwo<Message, ButtonActionMenu.MenuAction> handleStrike(@Nonnull ButtonClickEvent e, int stageId) {
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
        MessageBuilder builder = strikeMessageProducer.apply(new UpcomingStrikeInfo());

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

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        onTimeout.accept(new StrikeStagesTimeoutEvent());
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

        /**
         * This may only be empty in the case that the ruleset only has one stage
         */
        @Nonnull
        public List<Stage> getRemainingStages() {
            return ruleset.getStagesStream()
                    .filter(stage ->
                            strikes.stream()
                                    .flatMap(Set::stream)
                                    .noneMatch(struckStageId -> stage.getStageId() == struckStageId))
                    .collect(Collectors.toList());
        }
    }

    public class UpcomingStrikeInfo extends StrikeStagesInfo {
        public int getStagesToStrike() {
            return ruleset.getStarterStrikePattern()[currentStrikeIdx] - currentStrikes.size();
        }

        public boolean isNoStrikeRuleset() {
            return ruleset.getStarterStrikePattern().length == 0;
        }

        /**
         * @return true if this is the first strike or if strikes are skipped in this ruleset
         */
        public boolean isFirstStrike() {
            return strikes.isEmpty() || strikes.get(0).isEmpty();
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
        public Stage getRemainingStage() {
            return ruleset.getStagesStream()
                    .filter(stage ->
                            strikes.stream()
                                    .flatMap(Set::stream)
                                    .noneMatch(struckStageId -> stage.getStageId() == struckStageId))
                    .findAny()
                    .orElse(null);
        }
    }

    public class StrikeStagesTimeoutEvent extends StrikeStagesInfo {
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, StrikeStagesMenu> {
        // TODO: Defaults like this for everything in every builder?
        @Nonnull
        public static final Function<UpcomingStrikeInfo, MessageBuilder> DEFAULT_STRIKE_MESSAGE_PRODUCER = info -> {
            long currentStriker = info.getCurrentStriker();
            int stagesToStrike = info.getStagesToStrike();

            Ruleset ruleset = info.getRuleset();
            if (info.isNoStrikeRuleset()) {
                return new MessageBuilder(String.format("Wow that's just very simple, there is only one stage in the ruleset. You're going to %s.",
                        ruleset.getStarters().get(0).getDisplayName()));
            } else if (info.isFirstStrike()) {
                return new MessageBuilder(String.format(
                        "Alright, time to strike stages. %s, you go first. Please strike %d stage%s from the list below.",
                        MiscUtil.mentionUser(currentStriker),
                        stagesToStrike,
                        stagesToStrike > 1 ? "s" : ""
                )).mentionUsers(currentStriker);
            } else {
                return new MessageBuilder(String.format("%s, please strike %d stage%s from the list below.",
                        MiscUtil.mentionUser(currentStriker),
                        stagesToStrike,
                        stagesToStrike > 1 ? "s" : ""))
                        .mentionUsers(currentStriker);
            }
        };

        @Nullable
        private Ruleset ruleset;
        @Nonnull
        private Function<UpcomingStrikeInfo, MessageBuilder> strikeMessageProducer;
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

            strikeMessageProducer = DEFAULT_STRIKE_MESSAGE_PRODUCER;
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
        public Builder setStrikeMessageProducer(@Nonnull Function<UpcomingStrikeInfo, MessageBuilder> strikeMessageProducer) {
            this.strikeMessageProducer = strikeMessageProducer;
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
        public Function<UpcomingStrikeInfo, MessageBuilder> getStrikeMessageProducer() {
            return strikeMessageProducer;
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
            return new StrikeStagesMenu(getWaiter(), getTimeout(), getUnit(), strikeMessageProducer, onStrike, onUserStrikes, onResult, ruleset, getUser1(), getUser2(), onTimeout);
        }
    }
}
