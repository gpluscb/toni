package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.menu.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.Button.LABEL_MAX_LENGTH;

public class StrikeStagesMenu extends TwoUsersChoicesActionMenu {
    private static final Logger log = LogManager.getLogger(StrikeStagesMenu.class);

    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ButtonActionMenu underlying;

    private long currentStriker;
    private int currentStrikeIdx;
    @Nonnull
    private Set<Integer> currentStrikes;
    @Nonnull
    private final List<Set<Integer>> strikes;

    public StrikeStagesMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;

        currentStriker = getTwoUsersChoicesActionMenuSettings().user1();
        currentStrikeIdx = 0;
        currentStrikes = new LinkedHashSet<>();
        strikes = new ArrayList<>();
        strikes.add(currentStrikes);

        Message start = settings.strikeMessageProducer().apply(new UpcomingStrikeInfo());

        ButtonActionMenu.Settings.Builder underlyingBuilder = new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .addUsers(getTwoUsersChoicesActionMenuSettings().user1(), getTwoUsersChoicesActionMenuSettings().user2())
                .setStart(start)
                .setDeletionButton(null)
                .setOnTimeout(this::onTimeout);

        for (Stage starter : settings.ruleset().getStarters()) {
            int id = starter.stageId();
            underlyingBuilder.registerButton(
                    Button.secondary(String.valueOf(id), StringUtils.abbreviate(starter.name(), LABEL_MAX_LENGTH)),
                    e -> handleStrike(e, id)
            );
        }

        underlying = new ButtonActionMenu(underlyingBuilder.build());
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
    private ButtonActionMenu.MenuAction handleStrike(@Nonnull ButtonClickEvent e, int stageId) {
        if (e.getUser().getIdLong() != currentStriker) {
            e.reply("It's not your turn to strike right now!").setEphemeral(true).queue();
            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        if (strikes.stream().anyMatch(struckStages -> struckStages.contains(stageId))) {
            log.warn("Stage was double struck. Race condition or failure to set as disabled?");
            e.editMessage("That stage has already been struck. Please strike a different one.").queue();
            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        currentStrikes.add(stageId);
        settings.onStrike().accept(new StrikeInfo(stageId), e);

        long striker1 = getTwoUsersChoicesActionMenuSettings().user1();
        long striker2 = getTwoUsersChoicesActionMenuSettings().user2();

        int[] starterStrikePattern = settings.ruleset().getStarterStrikePattern();
        if (currentStrikes.size() == starterStrikePattern[currentStrikeIdx]) {
            settings.onUserStrikes().accept(new UserStrikesInfo(currentStrikes), e);

            if (strikes.size() == starterStrikePattern.length) {
                settings.onResult().accept(new StrikeResult(), e);

                return ButtonActionMenu.MenuAction.CANCEL;
            }

            currentStrikes = new HashSet<>();
            strikes.add(currentStrikes);
            currentStrikeIdx++;
            currentStriker = currentStriker == striker1 ? striker2 : striker1; // Invert
        }

        List<ActionRow> actionRows = MiscUtil.disabledButtonActionRows(e);

        Message newMessage = settings.strikeMessageProducer().apply(new UpcomingStrikeInfo());

        e.editMessage(newMessage).setActionRows(actionRows).queue();

        return ButtonActionMenu.MenuAction.CONTINUE;
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        settings.onTimeout().accept(new StrikeStagesTimeoutEvent());
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
    public Settings getStrikeStagesMenuSettings() {
        return settings;
    }

    private abstract class StrikeStagesInfo extends TwoUsersMenuStateInfo {
        @Nonnull
        public Settings getStrikeStagesMenuSettings() {
            return StrikeStagesMenu.this.getStrikeStagesMenuSettings();
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
            return settings.ruleset().getStagesStream()
                    .filter(stage ->
                            strikes.stream()
                                    .flatMap(Set::stream)
                                    .noneMatch(struckStageId -> stage.stageId() == struckStageId))
                    .collect(Collectors.toList());
        }
    }

    public class UpcomingStrikeInfo extends StrikeStagesInfo {
        public int getStagesToStrike() {
            return settings.ruleset().getStarterStrikePattern()[currentStrikeIdx] - currentStrikes.size();
        }

        public boolean isNoStrikeRuleset() {
            return settings.ruleset().getStarterStrikePattern().length == 0;
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
            return settings.ruleset().getStagesStream()
                    .filter(stage -> stage.stageId() == struckStageId)
                    .findAny()
                    .get();
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
            return settings.ruleset().getStagesStream()
                    .filter(stage -> struckStagesIds.contains(stage.stageId()))
                    .collect(Collectors.toList());
        }
    }

    public class StrikeResult extends StrikeStagesInfo {
        /**
         * This is only null in the case that the ruleset only has one stage
         */
        @Nullable
        public Stage getRemainingStage() {
            return settings.ruleset().getStagesStream()
                    .filter(stage ->
                            strikes.stream()
                                    .flatMap(Set::stream)
                                    .noneMatch(struckStageId -> stage.stageId() == struckStageId))
                    .findAny()
                    .orElse(null);
        }
    }

    public class StrikeStagesTimeoutEvent extends StrikeStagesInfo {
    }

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull Function<UpcomingStrikeInfo, Message> strikeMessageProducer,
                           @Nonnull BiConsumer<StrikeInfo, ButtonClickEvent> onStrike,
                           @Nonnull BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes,
                           @Nonnull BiConsumer<StrikeResult, ButtonClickEvent> onResult, @Nonnull Ruleset ruleset,
                           @Nonnull Consumer<StrikeStagesTimeoutEvent> onTimeout) {
        @Nonnull
        public static final Function<UpcomingStrikeInfo, Message> DEFAULT_STRIKE_MESSAGE_PRODUCER = info -> {
            long currentStriker = info.getCurrentStriker();
            int stagesToStrike = info.getStagesToStrike();

            Ruleset ruleset = info.getStrikeStagesMenuSettings().ruleset();
            if (info.isNoStrikeRuleset()) {
                return new MessageBuilder(String.format("Wow that's just very simple, there is only one stage in the ruleset. You're going to %s.",
                        ruleset.getStarters().get(0).getDisplayName()))
                        .build();
            } else if (info.isFirstStrike()) {
                return new MessageBuilder(String.format("Alright, time to strike stages. %s, you go first. Please strike %d stage%s from the list below.",
                        MiscUtil.mentionUser(currentStriker),
                        stagesToStrike,
                        stagesToStrike > 1 ? "s" : ""))
                        .mentionUsers(currentStriker)
                        .build();
            } else {
                return new MessageBuilder(String.format("%s, please strike %d stage%s from the list below.",
                        MiscUtil.mentionUser(currentStriker),
                        stagesToStrike,
                        stagesToStrike > 1 ? "s" : ""))
                        .mentionUsers(currentStriker)
                        .build();
            }
        };
        @Nonnull
        public static final BiConsumer<StrikeInfo, ButtonClickEvent> DEFAULT_ON_STRIKE = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<UserStrikesInfo, ButtonClickEvent> DEFAULT_ON_USER_STRIKES = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<StrikeResult, ButtonClickEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<StrikeStagesTimeoutEvent> DEFAULT_ON_TIMEOUT = MiscUtil.emptyConsumer();

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;
            @Nullable
            private Ruleset ruleset;
            @Nonnull
            private Function<UpcomingStrikeInfo, Message> strikeMessageProducer = DEFAULT_STRIKE_MESSAGE_PRODUCER;
            @Nonnull
            private BiConsumer<StrikeInfo, ButtonClickEvent> onStrike = DEFAULT_ON_STRIKE;
            @Nonnull
            private BiConsumer<UserStrikesInfo, ButtonClickEvent> onUserStrikes = DEFAULT_ON_USER_STRIKES;
            @Nonnull
            private BiConsumer<StrikeResult, ButtonClickEvent> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<StrikeStagesTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            @Nonnull
            public Builder setTwoUsersChoicesActionMenuSettings(@Nullable TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setRuleset(@Nonnull Ruleset ruleset) {
                this.ruleset = ruleset;
                return this;
            }

            @Nonnull
            public Builder setStrikeMessageProducer(@Nonnull Function<UpcomingStrikeInfo, Message> strikeMessageProducer) {
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
            public Function<UpcomingStrikeInfo, Message> getStrikeMessageProducer() {
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
            public Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMenuSettings must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, strikeMessageProducer, onStrike, onUserStrikes, onResult, ruleset, onTimeout);
            }
        }
    }
}
