package com.github.gpluscb.toni.command.menu;

import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.ButtonActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class RPSAndStrikeStagesMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final RPSMenu rpsUnderlying;

    @Nullable
    private RPSMenu.RPSResult rpsResult;
    @Nullable
    private StrikeFirstChoiceResult strikeFirstChoiceResult;

    public RPSAndStrikeStagesMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;

        rpsUnderlying = createRPS(settings.start());
    }

    @Nonnull
    private RPSMenu createRPS(@Nonnull Message start) {
        return new RPSMenu(new RPSMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(getActionMenuSettings().waiter())
                                .setTimeout(settings.rpsTimeout(), settings.rpsUnit())
                                .build())
                        .setUsers(getTwoUsersChoicesActionMenuSettings().user1(), getTwoUsersChoicesActionMenuSettings().user2())
                        .build())
                .setStart(start)
                .setOnChoiceMade(settings.onRPSChoiceMade())
                .setOnResult(this::onRPSResult)
                .setOnTimeout(settings.onRPSTimeout())
                .build());
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        rpsUnderlying.display(channel);
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        rpsUnderlying.display(channel, messageId);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        rpsUnderlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandInteractionEvent event) {
        rpsUnderlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        rpsUnderlying.displayDeferredReplying(hook);
    }

    @Nonnull
    @Override
    public List<ActionRow> getComponents() {
        return rpsUnderlying.getComponents();
    }

    @Override
    public void start(@Nonnull Message message) {
        rpsUnderlying.start(message);
    }

    private synchronized void onRPSResult(@Nonnull RPSMenu.RPSResult rpsResult, @Nonnull ButtonInteractionEvent e) {
        this.rpsResult = rpsResult;
        settings.onRPSResult().accept(rpsResult, e);

        e.deferEdit().queue();

        if (rpsResult.getWinner() == RPSMenu.Winner.Tie) {
            Message start = settings.rpsTieMessageProvider().apply(rpsResult, e);

            RPSMenu rpsUnderlying = createRPS(start);

            rpsUnderlying.display(e.getMessage());
            return;
        }

        // We know it's not null because we checked for tie earlier
        @SuppressWarnings("ConstantConditions") long winner = rpsResult.getWinnerId();
        @SuppressWarnings("ConstantConditions") long loser = rpsResult.getLoserId();

        Message start = settings.strikeFirstMessageProvider().apply(rpsResult, e);

        Function<ButtonInteractionEvent, MenuAction> onButtonFirst = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, winner, loser), event);
            return MenuAction.CANCEL;
        };

        Function<ButtonInteractionEvent, MenuAction> onButtonSecond = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, loser, winner), event);
            return MenuAction.CANCEL;
        };

        ButtonActionMenu strikeFirstChoiceUnderlying = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .setDeletionButton(null)
                .addUsers(winner)
                .setStart(start)
                .registerButton(Button.secondary("first", Emoji.fromUnicode(Constants.ONE)), onButtonFirst)
                .registerButton(Button.secondary("second", Emoji.fromUnicode(Constants.TWO)), onButtonSecond)
                .setOnTimeout(event -> settings.onStrikeFirstTimeout().accept(new StrikeFirstChoiceTimeoutEvent(winner)))
                .build());

        strikeFirstChoiceUnderlying.display(e.getMessage());
    }

    private synchronized void onStrikeFirstChoice(@Nonnull StrikeFirstChoiceResult result, @Nonnull ButtonInteractionEvent event) {
        event.deferEdit().queue();

        this.strikeFirstChoiceResult = result;
        settings.onStrikeFirstChoice().accept(result, event);

        StrikeStagesMenu strikeUnderlying = new StrikeStagesMenu(new StrikeStagesMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(getActionMenuSettings().waiter())
                                .setTimeout(settings.strikeTimeout(), settings.strikeUnit())
                                .build())
                        .setUsers(result.getFirstStriker(), result.getSecondStriker())
                        .build())
                .setStrikeMessageProducer(settings.strikeMessageProducer())
                .setRuleset(settings.ruleset())
                .setOnStrike(settings.onStrike())
                .setOnUserStrikes(settings.onUserStrikes())
                .setOnResult(this::onStrikeResult)
                .setOnTimeout(settings.onStrikeTimeout())
                .build());

        strikeUnderlying.display(event.getMessage());
    }

    private synchronized void onStrikeResult(@Nonnull StrikeStagesMenu.StrikeResult strikeResult, @Nonnull ButtonInteractionEvent e) {
        settings.onStrikeResult().accept(strikeResult, e);
        // We know onRPSResult was called before
        //noinspection ConstantConditions
        settings.onResult().accept(new RPSAndStrikeStagesResult(rpsResult, strikeFirstChoiceResult, strikeResult), e);
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return rpsUnderlying.getJDA();
    }

    @Override
    public long getMessageId() {
        return rpsUnderlying.getMessageId();
    }

    @Override
    public long getChannelId() {
        return rpsUnderlying.getChannelId();
    }

    @Nonnull
    public Settings getRPSAndStrikeStagesMenuSettings() {
        return settings;
    }

    private abstract class RPSAndStrikeInfo extends TwoUsersMenuStateInfo {
        @Nonnull
        public Settings getRPSAndStrikeStagesMenuSettings() {
            return settings;
        }
    }

    public class StrikeFirstChoiceResult extends RPSAndStrikeInfo {
        private final long userMakingChoice;
        private final long firstStriker;
        private final long secondStriker;

        public StrikeFirstChoiceResult(long userMakingChoice, long firstStriker, long secondStriker) {
            this.userMakingChoice = userMakingChoice;
            this.firstStriker = firstStriker;
            this.secondStriker = secondStriker;
        }

        public long getUserMakingChoice() {
            return userMakingChoice;
        }

        public long getFirstStriker() {
            return firstStriker;
        }

        public long getSecondStriker() {
            return secondStriker;
        }

        public boolean userMakingChoiceStrikesFirst() {
            return userMakingChoice == firstStriker;
        }
    }

    public class RPSAndStrikeStagesResult extends RPSAndStrikeInfo {
        @Nonnull
        private final RPSMenu.RPSResult rpsResult;
        @Nonnull
        private final StrikeFirstChoiceResult strikeFirstChoiceResult;
        @Nonnull
        private final StrikeStagesMenu.StrikeResult strikeResult;

        public RPSAndStrikeStagesResult(@Nonnull RPSMenu.RPSResult rpsResult, @Nonnull StrikeFirstChoiceResult strikeFirstChoiceResult, @Nonnull StrikeStagesMenu.StrikeResult strikeResult) {
            this.rpsResult = rpsResult;
            this.strikeFirstChoiceResult = strikeFirstChoiceResult;
            this.strikeResult = strikeResult;
        }

        @Nonnull
        public RPSMenu.RPSResult getRpsResult() {
            return rpsResult;
        }

        @Nonnull
        public StrikeFirstChoiceResult getStrikeFirstChoiceResult() {
            return strikeFirstChoiceResult;
        }

        @Nonnull
        public StrikeStagesMenu.StrikeResult getStrikeResult() {
            return strikeResult;
        }
    }

    public class StrikeFirstChoiceTimeoutEvent extends RPSAndStrikeInfo {
        private final long userMakingChoice;

        public StrikeFirstChoiceTimeoutEvent(long userMakingChoice) {
            this.userMakingChoice = userMakingChoice;
        }

        public long getUserMakingChoice() {
            return userMakingChoice;
        }
    }

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> strikeFirstMessageProvider,
                           @Nonnull BiConsumer<StrikeFirstChoiceResult, ButtonInteractionEvent> onStrikeFirstChoice,
                           @Nonnull Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout,
                           long rpsTimeout, @Nonnull TimeUnit rpsUnit,
                           @Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> rpsTieMessageProvider,
                           @Nonnull BiConsumer<RPSMenu.RPS, ButtonInteractionEvent> onRPSChoiceMade,
                           @Nonnull BiConsumer<RPSMenu.RPSResult, ButtonInteractionEvent> onRPSResult, @Nonnull Message start,
                           @Nonnull Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout,
                           long strikeTimeout, @Nonnull TimeUnit strikeUnit,
                           @Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer,
                           @Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonInteractionEvent> onStrike,
                           @Nonnull BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonInteractionEvent> onUserStrikes,
                           @Nonnull BiConsumer<StrikeStagesMenu.StrikeResult, ButtonInteractionEvent> onStrikeResult,
                           @Nonnull Ruleset ruleset,
                           @Nonnull Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout,
                           @Nonnull BiConsumer<RPSAndStrikeStagesResult, ButtonInteractionEvent> onResult) {
        @Nonnull
        public static final BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> DEFAULT_STRIKE_FIRST_MESSAGE_PROVIDER = (rpsResult, e) -> {
            long user1 = rpsResult.getTwoUsersChoicesActionMenuSettings().user1();
            long user2 = rpsResult.getTwoUsersChoicesActionMenuSettings().user2();

            // This will be decisive at this point
            //noinspection ConstantConditions
            return new MessageBuilder(String.format(
                    "%s chose %s, and %s chose %s. So %s, you won the RPS. Will you strike first or second?",
                    MiscUtil.mentionUser(user1),
                    rpsResult.getChoice1().getDisplayName(),
                    MiscUtil.mentionUser(user2),
                    rpsResult.getChoice2().getDisplayName(),
                    MiscUtil.mentionUser(rpsResult.getWinnerId())
            )).mentionUsers(user1, user2).build();
        };
        @Nonnull
        public static final BiConsumer<StrikeFirstChoiceResult, ButtonInteractionEvent> DEFAULT_ON_STRIKE_FIRST_CHOICE = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<StrikeFirstChoiceTimeoutEvent> DEFAULT_ON_STRIKE_FIRST_TIMEOUT = MiscUtil.emptyConsumer();
        public static final long DEFAULT_RPS_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_RPS_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> DEFAULT_RPS_TIE_MESSAGE_PROVIDER = (rpsResult, e) ->
                new MessageBuilder(String.format("Both of you chose %s. So please try again.",
                        rpsResult.getChoice1().getDisplayName()))
                        .build();
        @Nonnull
        public static final BiConsumer<RPSMenu.RPS, ButtonInteractionEvent> DEFAULT_ON_RPS_CHOICE_MADE = RPSMenu.Settings.DEFAULT_ON_CHOICE_MADE;
        @Nonnull
        public static final BiConsumer<RPSMenu.RPSResult, ButtonInteractionEvent> DEFAULT_ON_RPS_RESULT = RPSMenu.Settings.DEFAULT_ON_RESULT;
        @Nonnull
        public static final Consumer<RPSMenu.RPSTimeoutEvent> DEFAULT_ON_RPS_TIMEOUT = RPSMenu.Settings.DEFAULT_ON_TIMEOUT;
        public static final long DEFAULT_STRIKE_TIMEOUT = 5;
        @Nonnull
        public static final TimeUnit DEFAULT_STRIKE_UNIT = TimeUnit.MINUTES;
        @Nonnull
        public static final Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> DEFAULT_STRIKE_MESSAGE_PRODUCER = StrikeStagesMenu.Settings.DEFAULT_STRIKE_MESSAGE_PRODUCER;
        @Nonnull
        public static final BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonInteractionEvent> DEFAULT_ON_STRIKE = StrikeStagesMenu.Settings.DEFAULT_ON_STRIKE;
        @Nonnull
        public static final BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonInteractionEvent> DEFAULT_ON_USER_STRIKES = StrikeStagesMenu.Settings.DEFAULT_ON_USER_STRIKES;
        @Nonnull
        public static final BiConsumer<StrikeStagesMenu.StrikeResult, ButtonInteractionEvent> DEFAULT_ON_STRIKE_RESULT = StrikeStagesMenu.Settings.DEFAULT_ON_RESULT;
        @Nonnull
        public static final Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> DEFAULT_ON_STRIKE_TIMEOUT = StrikeStagesMenu.Settings.DEFAULT_ON_TIMEOUT;
        @Nonnull
        public static final BiConsumer<RPSAndStrikeStagesResult, ButtonInteractionEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;
            @Nonnull
            private BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> strikeFirstMessageProvider = DEFAULT_STRIKE_FIRST_MESSAGE_PROVIDER;
            @Nonnull
            private BiConsumer<StrikeFirstChoiceResult, ButtonInteractionEvent> onStrikeFirstChoice = DEFAULT_ON_STRIKE_FIRST_CHOICE;
            @Nonnull
            private Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout = DEFAULT_ON_STRIKE_FIRST_TIMEOUT;

            @Nullable
            private Message start;
            private long rpsTimeout = DEFAULT_RPS_TIMEOUT;
            @Nonnull
            private TimeUnit rpsUnit = DEFAULT_RPS_UNIT;
            @Nonnull
            private BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> rpsTieMessageProvider = DEFAULT_RPS_TIE_MESSAGE_PROVIDER;
            @Nonnull
            private BiConsumer<RPSMenu.RPS, ButtonInteractionEvent> onRPSChoiceMade = DEFAULT_ON_RPS_CHOICE_MADE;
            @Nonnull
            private BiConsumer<RPSMenu.RPSResult, ButtonInteractionEvent> onRPSResult = DEFAULT_ON_RPS_RESULT;
            @Nonnull
            private Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout = DEFAULT_ON_RPS_TIMEOUT;
            @Nullable
            private Ruleset ruleset;
            private long strikeTimeout = DEFAULT_STRIKE_TIMEOUT;
            @Nonnull
            private TimeUnit strikeUnit = DEFAULT_STRIKE_UNIT;
            @Nonnull
            private Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer = DEFAULT_STRIKE_MESSAGE_PRODUCER;
            @Nonnull
            private BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonInteractionEvent> onStrike = DEFAULT_ON_STRIKE;
            @Nonnull
            private BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonInteractionEvent> onUserStrikes = DEFAULT_ON_USER_STRIKES;
            @Nonnull
            private BiConsumer<StrikeStagesMenu.StrikeResult, ButtonInteractionEvent> onStrikeResult = DEFAULT_ON_STRIKE_RESULT;
            @Nonnull
            private Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout = DEFAULT_ON_STRIKE_TIMEOUT;

            @Nonnull
            private BiConsumer<RPSAndStrikeStagesResult, ButtonInteractionEvent> onResult = DEFAULT_ON_RESULT;

            /**
             * Timeout is for strike first choice
             */
            @Nonnull
            public Builder setTwoUsersChoicesActionMenuSettings(@Nullable TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setStrikeFirstMessageProvider(@Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> strikeFirstMessageProvider) {
                this.strikeFirstMessageProvider = strikeFirstMessageProvider;
                return this;
            }

            @Nonnull
            public Builder setOnStrikeFirstChoice(@Nonnull BiConsumer<StrikeFirstChoiceResult, ButtonInteractionEvent> onStrikeFirstChoice) {
                this.onStrikeFirstChoice = onStrikeFirstChoice;
                return this;
            }

            @Nonnull
            public Builder setOnStrikeFirstTimeout(@Nonnull Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout) {
                this.onStrikeFirstTimeout = onStrikeFirstTimeout;
                return this;
            }

            @Nonnull
            public Builder setStart(@Nonnull Message start) {
                this.start = start;
                return this;
            }

            @Nonnull
            public Builder setRpsTimeout(long rpsTimeout, @Nonnull TimeUnit rpsUnit) {
                this.rpsTimeout = rpsTimeout;
                this.rpsUnit = rpsUnit;
                return this;
            }

            @Nonnull
            public Builder setRpsTieMessageProvider(@Nonnull BiFunction<RPSMenu.RPSResult, ButtonInteractionEvent, Message> rpsTieMessageProvider) {
                this.rpsTieMessageProvider = rpsTieMessageProvider;
                return this;
            }

            @Nonnull
            public Builder setOnRPSChoiceMade(@Nonnull BiConsumer<RPSMenu.RPS, ButtonInteractionEvent> onRPSChoiceMade) {
                this.onRPSChoiceMade = onRPSChoiceMade;
                return this;
            }

            @Nonnull
            public Builder setOnRPSResult(@Nonnull BiConsumer<RPSMenu.RPSResult, ButtonInteractionEvent> onRPSResult) {
                this.onRPSResult = onRPSResult;
                return this;
            }

            @Nonnull
            public Builder setOnRPSTimeout(@Nonnull Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout) {
                this.onRPSTimeout = onRPSTimeout;
                return this;
            }

            @Nonnull
            public Builder setRuleset(@Nonnull Ruleset ruleset) {
                this.ruleset = ruleset;
                return this;
            }

            @Nonnull
            public Builder setStrikeTimeout(long strikeTimeout, @Nonnull TimeUnit strikeUnit) {
                this.strikeTimeout = strikeTimeout;
                this.strikeUnit = strikeUnit;
                return this;
            }

            @Nonnull
            public Builder setStrikeMessageProducer(@Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer) {
                this.strikeMessageProducer = strikeMessageProducer;
                return this;
            }

            @Nonnull
            public Builder setOnStrike(@Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonInteractionEvent> onStrike) {
                this.onStrike = onStrike;
                return this;
            }

            @Nonnull
            public Builder setOnUserStrikes(@Nonnull BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonInteractionEvent> onUserStrikes) {
                this.onUserStrikes = onUserStrikes;
                return this;
            }

            @Nonnull
            public Builder setOnStrikeResult(@Nonnull BiConsumer<StrikeStagesMenu.StrikeResult, ButtonInteractionEvent> onStrikeResult) {
                this.onStrikeResult = onStrikeResult;
                return this;
            }

            @Nonnull
            public Builder setOnStrikeTimeout(@Nonnull Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout) {
                this.onStrikeTimeout = onStrikeTimeout;
                return this;
            }

            @Nonnull
            public Builder setOnResult(@Nonnull BiConsumer<RPSAndStrikeStagesResult, ButtonInteractionEvent> onResult) {
                this.onResult = onResult;
                return this;
            }

            @Nonnull
            public Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMenuSettings must be set");
                if (start == null) throw new IllegalStateException("Start must be set");
                if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, strikeFirstMessageProvider, onStrikeFirstChoice, onStrikeFirstTimeout,
                        rpsTimeout, rpsUnit, rpsTieMessageProvider, onRPSChoiceMade, onRPSResult, start, onRPSTimeout,
                        strikeTimeout, strikeUnit, strikeMessageProducer, onStrike, onUserStrikes, onStrikeResult, ruleset, onStrikeTimeout,
                        onResult);
            }
        }
    }
}
