package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
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
    private final BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade;
    @Nonnull
    private final BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult;
    @Nonnull
    private final Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout;

    @Nonnull
    private final BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice;
    @Nonnull
    private final Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout;

    private final long strikeTimeout;
    @Nonnull
    private final TimeUnit strikeUnit;
    @Nonnull
    private final BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike;
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
                                  long strikeFirstChoiceTimeout, @Nonnull TimeUnit strikeFirstChoiceUnit, @Nonnull BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice, @Nonnull Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout,
                                  long rpsTimeout, @Nonnull TimeUnit rpsUnit, @Nonnull BiConsumer<RPSMenu.RPS, ButtonClickEvent> onRPSChoiceMade, @Nonnull BiConsumer<RPSMenu.RPSResult, ButtonClickEvent> onRPSResult, @Nonnull Message start, @Nonnull Consumer<RPSMenu.RPSTimeoutEvent> onRPSTimeout,
                                  long strikeTimeout, @Nonnull TimeUnit strikeUnit, @Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike, @Nonnull BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult, @Nonnull Ruleset ruleset, @Nonnull Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout,
                                  @Nonnull BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult) {
        super(waiter, user1, user2, strikeFirstChoiceTimeout, strikeFirstChoiceUnit);

        this.onResult = onResult;

        this.rpsTimeout = rpsTimeout;
        this.rpsUnit = rpsUnit;
        this.onRPSChoiceMade = onRPSChoiceMade;
        this.onRPSResult = onRPSResult;
        this.onRPSTimeout = onRPSTimeout;

        this.onStrikeFirstChoice = onStrikeFirstChoice;
        this.onStrikeFirstTimeout = onStrikeFirstTimeout;

        this.strikeTimeout = strikeTimeout;
        this.strikeUnit = strikeUnit;
        this.onStrike = onStrike;
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
    public void display(@Nonnull Message message) {
        rpsUnderlying.display(message);
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

        if (rpsResult.getWinner() == RPSMenu.RPSResult.Winner.Tie) {
            Message start = new MessageBuilder(String.format("Both of you chose %s. So please try again.",
                    rpsResult.getChoiceA().getDisplayName()))
                    .build();

            RPSMenu rpsUnderlying = createRPS(start);

            rpsUnderlying.display(e.getMessage());
            return;
        }

        // We know it's not null because we checked for tie earlier
        @SuppressWarnings("ConstantConditions") long winner = rpsResult.getWinnerId();
        @SuppressWarnings("ConstantConditions") long loser = rpsResult.getLoserId();
        long user1 = getUser1();
        long user2 = getUser2();

        Message start = new MessageBuilder(String.format(
                "%s chose %s, and %s chose %s. So %s, you won the RPS. Will you strike first or second?",
                MiscUtil.mentionUser(user1),
                rpsResult.getChoiceA().getDisplayName(),
                MiscUtil.mentionUser(user2),
                rpsResult.getChoiceB().getDisplayName(),
                MiscUtil.mentionUser(winner)
        )).mentionUsers(user1, user2).build();

        Function<ButtonClickEvent, OneOfTwo<Message, ButtonActionMenu.MenuAction>> onButtonFirst = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, winner, loser), event);
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
        };

        Function<ButtonClickEvent, OneOfTwo<Message, ButtonActionMenu.MenuAction>> onButtonSecond = event -> {
            onStrikeFirstChoice(new StrikeFirstChoiceResult(winner, loser, winner), event);
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
        };

        ButtonActionMenu strikeFirstChoiceUnderlying = new ButtonActionMenu.Builder()
                .setWaiter(getWaiter())
                .setDeletionButton(null)
                .addUsers(winner)
                .setStart(start)
                .setTimeout(getTimeout(), getUnit())
                .registerButton(Button.secondary("first", Emoji.fromUnicode(Constants.ONE)), onButtonFirst)
                .registerButton(Button.secondary("second", Emoji.fromUnicode(Constants.TWO)), onButtonSecond)
                .setTimeoutAction((channel, messageId) -> onStrikeFirstTimeout.accept(new StrikeFirstChoiceTimeoutEvent(user1, user2, winner, channel, messageId)))
                .build();

        strikeFirstChoiceUnderlying.display(e.getMessage());
    }

    private synchronized void onStrikeFirstChoice(@Nonnull StrikeFirstChoiceResult result, @Nonnull ButtonClickEvent event) {
        this.strikeFirstChoiceResult = result;
        onStrikeFirstChoice.accept(result, event);

        StrikeStagesMenu strikeUnderlying = new StrikeStagesMenu.Builder()
                .setWaiter(getWaiter())
                .setUsers(result.getFirstStriker(), result.getSecondStriker())
                .setTimeout(strikeTimeout, strikeUnit)
                .setRuleset(ruleset)
                .setOnStrike(onStrike)
                .setOnResult(this::onStrikeResult)
                .setOnTimeout(onStrikeTimeout)
                .build();

        // TODO: needs ack?
        strikeUnderlying.display(event.getMessage());
    }

    private synchronized void onStrikeResult(@Nonnull StrikeStagesMenu.StrikeResult strikeResult, @Nonnull ButtonClickEvent e) {
        onStrikeResult.accept(strikeResult, e);
        // We know onRPSResult was called before
        //noinspection ConstantConditions
        onResult.accept(new RPSAndStrikeStagesResult(rpsResult, strikeFirstChoiceResult, strikeResult), e);
    }

    public static class StrikeFirstChoiceResult {
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

    public static class RPSAndStrikeStagesResult {
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

    public static class StrikeFirstChoiceTimeoutEvent {
        private final long user1;
        private final long user2;
        private final long userMakingChoice;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public StrikeFirstChoiceTimeoutEvent(long user1, long user2, long userMakingChoice, @Nullable MessageChannel channel, long messageId) {
            this.user1 = user1;
            this.user2 = user2;
            this.userMakingChoice = userMakingChoice;
            this.channel = channel;
            this.messageId = messageId;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
        }

        public long getUserMakingChoice() {
            return userMakingChoice;
        }

        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, RPSAndStrikeStagesMenu> {
        @Nonnull
        private BiConsumer<StrikeFirstChoiceResult, ButtonClickEvent> onStrikeFirstChoice;
        @Nonnull
        private Consumer<StrikeFirstChoiceTimeoutEvent> onStrikeFirstTimeout;

        @Nullable
        private Message start;
        private long rpsTimeout;
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
        private BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike;
        @Nonnull
        private BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> onStrikeResult;
        @Nonnull
        private Consumer<StrikeStagesMenu.StrikeStagesTimeoutEvent> onStrikeTimeout;

        @Nonnull
        private BiConsumer<RPSAndStrikeStagesResult, ButtonClickEvent> onResult;

        public Builder() {
            super(Builder.class);

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
            onStrike = (info, e) -> {
            };
            onStrikeResult = (result, e) -> {
            };
            onStrikeTimeout = timeout -> {
            };
            onResult = (r, e) -> {
            };
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
        public Builder setOnStrike(@Nonnull BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> onStrike) {
            this.onStrike = onStrike;
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
        public BiConsumer<StrikeStagesMenu.StrikeInfo, ButtonClickEvent> getOnStrike() {
            return onStrike;
        }

        @Nonnull
        public BiConsumer<StrikeStagesMenu.StrikeResult, ButtonClickEvent> getOnStrikeResult() {
            return onStrikeResult;
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
            return new RPSAndStrikeStagesMenu(getWaiter(), getUser1(), getUser2(), getTimeout(), getUnit(), onStrikeFirstChoice, onStrikeFirstTimeout,
                    rpsTimeout, rpsUnit, onRPSChoiceMade, onRPSResult, start, onRPSTimeout,
                    strikeTimeout, strikeUnit, onStrike, onStrikeResult, ruleset, onStrikeTimeout,
                    onResult);
        }
    }
}
