package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.menu.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class RPSAndStrikeStagesMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final RPSMenu rpsUnderlying;

    @Nonnull
    private final BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult;

    private final long rpsTimeout;
    @Nonnull
    private final TimeUnit rpsUnit;
    @Nonnull
    private final BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> rpsTieMessageProvider;
    @Nonnull
    private final BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade;
    @Nonnull
    private final BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult;
    @Nonnull
    private final Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout;

    @Nonnull
    private final BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> strikeFirstMessageProvider;
    @Nonnull
    private final BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice;
    @Nonnull
    private final Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout;

    private final long strikeTimeout;
    @Nonnull
    private final TimeUnit strikeUnit;
    @Nonnull
    private final Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer;
    @Nonnull
    private final BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike;
    @Nonnull
    private final BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonClickEvent> onUserStrikes;
    @Nonnull
    private final BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult;
    @Nonnull
    private final Ruleset ruleset;
    @Nonnull
    private final Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout;

    @Nullable
    private RPSMenu.RPSResult rpsResult;
    @Nullable
    private StrikeFirstChoiceResult strikeFirstChoiceResult;

    public RPSAndStrikeStagesMenu(@Nonnull EventWaiter waiter, long user1, long user2,
                                  long strikeFirstChoiceTimeout, @Nonnull TimeUnit strikeFirstChoiceUnit, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> strikeFirstMessageProvider, @Nonnull BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice, @Nonnull Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout,
                                  long rpsTimeout, @Nonnull TimeUnit rpsUnit, @Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> rpsTieMessageProvider, @Nonnull BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade, @Nonnull BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult, @Nonnull Message start, @Nonnull Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout,
                                  long strikeTimeout, @Nonnull TimeUnit strikeUnit, @Nonnull Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer, @Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike, @Nonnull BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonClickEvent> onUserStrikes, @Nonnull BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult, @Nonnull Ruleset ruleset, @Nonnull Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout,
                                  @Nonnull BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult) {
        super(waiter, user1, user2, strikeFirstChoiceTimeout, strikeFirstChoiceUnit);

        this.onResult = onResult;

        this.rpsTimeout = rpsTimeout;
        this.rpsUnit = rpsUnit;
        this.rpsTieMessageProvider = rpsTieMessageProvider;
        this.onRPSChoiceMade = onRPSChoiceMade;
        this.onRPSResult = onRPSResult;
        this.onRPSTimeout = onRPSTimeout;

        this.strikeFirstMessageProvider = strikeFirstMessageProvider;
        this.onStrikeFirstChoice = onStrikeFirstChoice;
        this.onStrikeFirstTimeout = onStrikeFirstTimeout;

        this.strikeTimeout = strikeTimeout;
        this.strikeUnit = strikeUnit;
        this.strikeMessageProducer = strikeMessageProducer;
        this.onStrike = onStrike;
        this.onUserStrikes = onUserStrikes;
        this.onStrikeResult = onStrikeResult;
        this.ruleset = ruleset;
        this.onStrikeTimeout = onStrikeTimeout;

        rpsUnderlying = createRPS(start);
    }

    @Nonnull
    private RPSMenu createRPS(@Nonnull Message start) {
        return new RPSMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(getUser1(), getUser2())
                .setStart(start)
                .setTimeout(rpsTimeout, rpsUnit)
                .setOnChoiceMade(onRPSChoiceMade)
                .setOnResult(this::onRPSResult)
                .setOnTimeout(onRPSTimeout)
                .build();
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
    public void displaySlashReplying(@Nonnull SlashCommandEvent event) {
        rpsUnderlying.displaySlashReplying(event);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        rpsUnderlying.displayDeferredReplying(hook);
    }

    private synchronized void onRPSResult(@Nonnull RPSMenu.RPSResult rpsResult, @Nonnull ButtonClickEvent e) {
        this.rpsResult = rpsResult;
        onRPSResult.accept(rpsResult, e);

        e.deferEdit().queue();

        if (rpsResult.getWinner() == RPSMenu.Winner.Tie) {
            Message start = rpsTieMessageProvider.apply(rpsResult, e);

            RPSMenu rpsUnderlying = createRPS(start);

            rpsUnderlying.display(e.getMessage());
            return;
        }

        // We know it's not null because we checked for tie earlier
        @SuppressWarnings("ConstantConditions") long winner = rpsResult.getWinnerId();
        @SuppressWarnings("ConstantConditions") long loser = rpsResult.getLoserId();

        Message start = strikeFirstMessageProvider.apply(rpsResult, e);

        Function<ButtonClickEvent, ButtonActionMenu.MenuAction> onButtonFirst = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, winner, loser), event);
            return ButtonActionMenu.MenuAction.CANCEL;
        };

        Function<ButtonClickEvent, ButtonActionMenu.MenuAction> onButtonSecond = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, loser, winner), event);
            return ButtonActionMenu.MenuAction.CANCEL;
        };

        ButtonActionMenu strikeFirstChoiceUnderlying = new ButtonActionMenu.Builder()
                .setWaiter(getWaiter())
                .setDeletionButton(null)
                .addUsers(winner)
                .setStart(start)
                .setTimeout(getTimeout(), getUnit())
                .registerButton(Button.secondary("first", Emoji.fromUnicode(Constants.ONE)), onButtonFirst)
                .registerButton(Button.secondary("second", Emoji.fromUnicode(Constants.TWO)), onButtonSecond)
                .setTimeoutAction(event -> onStrikeFirstTimeout.accept(new StrikeFirstChoiceTimeoutEvent(winner)))
                .build();

        strikeFirstChoiceUnderlying.display(e.getMessage());
    }

    private synchronized void onStrikeFirstChoice(@Nonnull StrikeFirstChoiceResult result, @Nonnull ButtonClickEvent event) {
        event.deferEdit().queue();

        this.strikeFirstChoiceResult = result;
        onStrikeFirstChoice.accept(result, event);

        StrikeStagesMenu strikeUnderlying = new StrikeStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(result.getFirstStriker(), result.getSecondStriker())
                .setTimeout(strikeTimeout, strikeUnit)
                .setStrikeMessageProducer(strikeMessageProducer)
                .setRuleset(ruleset)
                .setOnStrike(onStrike)
                .setOnUserStrikes(onUserStrikes)
                .setOnResult(this::onStrikeResult)
                .setOnTimeout(onStrikeTimeout)
                .build();

        strikeUnderlying.display(event.getMessage());
    }

    private synchronized void onStrikeResult(@Nonnull StrikeStagesMenu.StrikeResult strikeResult, @Nonnull ButtonClickEvent e) {
        onStrikeResult.accept(strikeResult, e);
        // We know onRPSResult was called before
        //noinspection ConstantConditions
        onResult.accept(new RPSAndStrikeStagesResult(rpsResult, strikeFirstChoiceResult, strikeResult), e);
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

    public class StrikeFirstChoiceResult extends TwoUsersMenuStateInfo {
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

    public class RPSAndStrikeStagesResult extends TwoUsersMenuStateInfo {
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

    public class StrikeFirstChoiceTimeoutEvent extends TwoUsersMenuStateInfo {
        private final long userMakingChoice;

        public StrikeFirstChoiceTimeoutEvent(long userMakingChoice) {
            this.userMakingChoice = userMakingChoice;
        }

        public long getUserMakingChoice() {
            return userMakingChoice;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, RPSAndStrikeStagesMenu> {
        @Nonnull
        private BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> strikeFirstMessageProvider;
        @Nonnull
        private BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice;
        @Nonnull
        private Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout;

        @Nullable
        private Message start;
        private long rpsTimeout;
        @Nonnull
        private BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> rpsTieMessageProvider;
        @Nonnull
        private TimeUnit rpsUnit;
        @Nonnull
        private BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade;
        @Nonnull
        private BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult;
        @Nonnull
        private Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout;
        @Nullable
        private Ruleset ruleset;
        private long strikeTimeout;
        @Nonnull
        private TimeUnit strikeUnit;
        @Nonnull
        private Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> strikeMessageProducer;
        @Nonnull
        private BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike;
        @Nonnull
        private BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonClickEvent> onUserStrikes;
        @Nonnull
        private BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult;
        @Nonnull
        private Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout;

        @Nonnull
        private BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult;

        public Builder() {
            super(Builder.class);

            rpsTieMessageProvider = (rpsResult, e) ->
                    new MessageBuilder(String.format("Both of you chose %s. So please try again.",
                            rpsResult.getChoice1().getDisplayName()))
                            .build();

            strikeFirstMessageProvider = (rpsResult, e) -> {
                // This will be decisive at this point
                //noinspection ConstantConditions
                return new MessageBuilder(String.format(
                        "%s chose %s, and %s chose %s. So %s, you won the RPS. Will you strike first or second?",
                        MiscUtil.mentionUser(rpsResult.getUser1()),
                        rpsResult.getChoice1().getDisplayName(),
                        MiscUtil.mentionUser(rpsResult.getUser2()),
                        rpsResult.getChoice2().getDisplayName(),
                        MiscUtil.mentionUser(rpsResult.getWinnerId())
                )).mentionUsers(rpsResult.getUser1(), rpsResult.getUser2()).build();
            };
            onStrikeFirstChoice = (r, e) -> {
            };
            onStrikeFirstTimeout = e -> {
            };
            rpsTimeout = 5;
            rpsUnit = TimeUnit.MINUTES;
            onRPSChoiceMade = (rps, e) -> {
            };
            onRPSResult = (r, e) -> {
            };
            onRPSTimeout = event -> {
                MessageChannel channel = event.getChannel();
                long id = event.getMessageId();
                if (channel == null) return;
                if (channel instanceof TextChannel) {
                    TextChannel textChannel = (TextChannel) channel;
                    if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                        return;
                }

                channel.retrieveMessageById(id)
                        .flatMap(m -> m.editMessage(m).setActionRows())
                        .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
            };
            strikeTimeout = 5;
            strikeUnit = TimeUnit.MINUTES;
            strikeMessageProducer = StrikeStagesMenu.Builder.DEFAULT_STRIKE_MESSAGE_PRODUCER;
            onStrike = (info, e) -> {
            };
            onUserStrikes = (info, e) -> {
            };
            onStrikeResult = (result, e) -> {
            };
            onStrikeTimeout = timeout -> {
            };
            onResult = (r, e) -> {
            };
        }

        @Nonnull
        public Builder setStrikeFirstMessageProvider(@Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> strikeFirstMessageProvider) {
            this.strikeFirstMessageProvider = strikeFirstMessageProvider;
            return this;
        }

        @Nonnull
        public Builder setStrikeFirstChoiceTimeout(long timeout, @Nonnull TimeUnit unit) {
            return setTimeout(timeout, unit);
        }

        @Nonnull
        public Builder setOnStrikeFirstChoice(@Nonnull BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice) {
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
        public Builder setRpsTieMessageProvider(@Nonnull BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> rpsTieMessageProvider) {
            this.rpsTieMessageProvider = rpsTieMessageProvider;
            return this;
        }

        @Nonnull
        public Builder setOnRPSChoiceMade(@Nonnull BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade) {
            this.onRPSChoiceMade = onRPSChoiceMade;
            return this;
        }

        @Nonnull
        public Builder setOnRPSResult(@Nonnull BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult) {
            this.onRPSResult = onRPSResult;
            return this;
        }

        /**
         * MessageChannel may be null on timeout in weird cases
         * <p>
         * Default: look at source lol it's too long for docs: {@link #build()}
         */
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
        public Builder setOnStrike(@Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike) {
            this.onStrike = onStrike;
            return this;
        }

        @Nonnull
        public Builder setOnUserStrikes(@Nonnull BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonClickEvent> onUserStrikes) {
            this.onUserStrikes = onUserStrikes;
            return this;
        }

        @Nonnull
        public Builder setOnStrikeResult(@Nonnull BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult) {
            this.onStrikeResult = onStrikeResult;
            return this;
        }

        @Nonnull
        public Builder setOnStrikeTimeout(@Nonnull Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout) {
            this.onStrikeTimeout = onStrikeTimeout;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        @Nonnull
        public BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> getStrikeFirstMessageProvider() {
            return strikeFirstMessageProvider;
        }

        @Nonnull
        public BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> getOnStrikeFirstChoice() {
            return onStrikeFirstChoice;
        }

        @Nonnull
        public Consumer<StrikeFirstChoiceTimeoutEvent> getOnStrikeFirstTimeout() {
            return onStrikeFirstTimeout;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        public long getRpsTimeout() {
            return rpsTimeout;
        }

        @Nonnull
        public TimeUnit getRpsUnit() {
            return rpsUnit;
        }

        @Nonnull
        public BiFunction<RPSMenu.RPSResult, ButtonClickEvent, Message> getRpsTieMessageProvider() {
            return rpsTieMessageProvider;
        }

        @Nonnull
        public BiConsumer<RPSMenu.RPS, ButtonClickEvent> getOnRPSChoiceMade() {
            return onRPSChoiceMade;
        }

        @Nonnull
        public BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> getOnRPSResult() {
            return onRPSResult;
        }

        @Nonnull
        public Consumer<RPSMenu.RPSTimeoutEvent> getOnRPSTimeout() {
            return onRPSTimeout;
        }

        @Nullable
        public Ruleset getRuleset() {
            return ruleset;
        }

        public long getStrikeTimeout() {
            return strikeTimeout;
        }

        @Nonnull
        public TimeUnit getStrikeUnit() {
            return strikeUnit;
        }

        @Nonnull
        public Function<StrikeStagesMenu.UpcomingStrikeInfo, Message> getStrikeMessageProducer() {
            return strikeMessageProducer;
        }

        @Nonnull
        public BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> getOnStrike() {
            return onStrike;
        }

        @Nonnull
        public BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> getOnStrikeResult() {
            return onStrikeResult;
        }

        @Nonnull
        public BiConsumer<StrikeStagesMenu.UserStrikesInfo, ButtonClickEvent> getOnUserStrikes() {
            return onUserStrikes;
        }

        @Nonnull
        public Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> getOnStrikeTimeout() {
            return onStrikeTimeout;
        }

        @Nonnull
        public BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        @Override
        public RPSAndStrikeStagesMenu build() {
            preBuild();
            if (start == null) throw new IllegalStateException("Start must be set");
            if (ruleset == null) throw new IllegalStateException("Ruleset must be set");

            // We know nonnullablility because preBuild
            //noinspection ConstantConditions
            return new RPSAndStrikeStagesMenu(getWaiter(), getUser1(), getUser2(), getTimeout(), getUnit(), strikeFirstMessageProvider, onStrikeFirstChoice, onStrikeFirstTimeout,
                    rpsTimeout, rpsUnit, rpsTieMessageProvider, onRPSChoiceMade, onRPSResult, start, onRPSTimeout,
                    strikeTimeout, strikeUnit, strikeMessageProducer, onStrike, onUserStrikes, onStrikeResult, ruleset, onStrikeTimeout,
                    onResult);
        }
    }
}
