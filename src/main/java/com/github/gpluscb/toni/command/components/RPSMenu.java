package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.TwoUsersChoicesActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
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

public class RPSMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final BiConsumer<RPS, ButtonClickEvent> onChoiceMade;
    @Nonnull
    private final BiConsumer<RPSResult, ButtonClickEvent> onResult;
    @Nonnull
    private final ButtonActionMenu underlying;

    @Nullable
    private RPS choice1;
    @Nullable
    private RPS choice2;

    public RPSMenu(@Nonnull EventWaiter waiter, long user1, long user2, long timeout, @Nonnull TimeUnit unit, @Nonnull BiConsumer<RPS, ButtonClickEvent> onChoiceMade, @Nonnull BiConsumer<RPSResult, ButtonClickEvent> onResult, @Nonnull Message start, @Nonnull Consumer<RPSTimeoutEvent> onTimeout) {
        super(waiter, user1, user2, timeout, unit);
        this.onChoiceMade = onChoiceMade;
        this.onResult = onResult;
        underlying = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .addUsers(user1, user2)
                .setStart(start)
                .setDeletionButton(null)
                .registerButton(Button.secondary("rock", Emoji.fromUnicode(Constants.ROCK)), e -> choose(e, RPS.ROCK))
                .registerButton(Button.secondary("paper", Emoji.fromUnicode(Constants.PAPER)), e -> choose(e, RPS.PAPER))
                .registerButton(Button.secondary("scissors", Emoji.fromUnicode(Constants.SCISSORS)), e -> choose(e, RPS.SCISSORS))
                .setTimeout(timeout, unit)
                .setTimeoutAction((channel, messageId) -> onTimeout.accept(new RPSTimeoutEvent(user1, user2, choice1, choice2, channel, messageId)))
                .build();
    }

    @Override
    public void display(@Nonnull Message message) {
        underlying.display(message);
    }

    @Override
    public void display(@Nonnull MessageChannel channel) {
        underlying.display(channel);
    }

    @Override
    public void displayReplying(@Nonnull MessageChannel channel, long messageId) {
        underlying.displayReplying(channel, messageId);
    }

    @Override
    public void displaySlashReplying(@Nonnull SlashCommandEvent e) {
        underlying.displaySlashReplying(e);
    }

    @Override
    public void displayDeferredReplying(@Nonnull InteractionHook hook) {
        underlying.displayDeferredReplying(hook);
    }

    @Nonnull
    private synchronized OneOfTwo<Message, ButtonActionMenu.MenuAction> choose(@Nonnull ButtonClickEvent e, @Nonnull RPS choice) {
        boolean isUser1 = e.getUser().getIdLong() == getUser1();
        if ((isUser1 && choice1 != null) || (!isUser1 && choice2 != null)) {
            e.reply("You have already chosen, and you must learn to live with that choice!")
                    .setEphemeral(true).queue();
            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
        }

        onChoiceMade.accept(choice, e);

        if (isUser1) choice1 = choice;
        else choice2 = choice;

        if (choice1 != null && choice2 != null) {
            RPSResult outcome = RPS.determineWinner(getUser1(), getUser2(), choice1, choice2);
            onResult.accept(outcome, e);

            return OneOfTwo.ofU(ButtonActionMenu.MenuAction.CANCEL);
        }

        e.reply("I have noted your choice...").setEphemeral(true).queue();
        return OneOfTwo.ofU(ButtonActionMenu.MenuAction.NOTHING);
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
        public static RPSResult determineWinner(long user1, long user2, @Nonnull RPS a, @Nonnull RPS b) {
            if (a == b) return new RPSResult(RPSResult.Winner.Tie, null, null, a, b);
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

            long winnerId = winner == RPSResult.Winner.A ? user1 : user2;
            long loserId = winnerId == user1 ? user2 : user1;

            return new RPSResult(winner, winnerId, loserId, a, b);
        }
    }

    public static class RPSResult {
        public enum Winner {
            A,
            B,
            Tie,
        }

        @Nonnull
        private final RPSResult.Winner winner;
        @Nullable
        private final Long winnerId;
        @Nullable
        private final Long loserId;
        @Nonnull
        private final RPS choiceA;
        @Nonnull
        private final RPS choiceB;

        public RPSResult(@Nonnull Winner winner, @Nullable Long winnerId, @Nullable Long loserId, @Nonnull RPS choiceA, @Nonnull RPS choiceB) {
            this.winner = winner;
            this.winnerId = winnerId;
            this.loserId = loserId;
            this.choiceA = choiceA;
            this.choiceB = choiceB;
        }

        @Nonnull
        public RPSResult.Winner getWinner() {
            return winner;
        }

        @Nullable
        public Long getWinnerId() {
            return winnerId;
        }

        @Nullable
        public Long getLoserId() {
            return loserId;
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

    public static class RPSTimeoutEvent {
        private final long user1;
        private final long user2;
        @Nullable
        private final RPS choiceA;
        @Nullable
        private final RPS choiceB;
        @Nullable
        private final MessageChannel channel;
        private final long messageId;

        public RPSTimeoutEvent(long user1, long user2, @Nullable RPS choiceA, @Nullable RPS choiceB, @Nullable MessageChannel channel, long messageId) {
            this.user1 = user1;
            this.user2 = user2;
            this.choiceA = choiceA;
            this.choiceB = choiceB;
            this.channel = channel;
            this.messageId = messageId;
        }

        public long getUser1() {
            return user1;
        }

        public long getUser2() {
            return user2;
        }

        @Nullable
        public RPS getChoiceA() {
            return choiceA;
        }

        @Nullable
        public RPS getChoiceB() {
            return choiceB;
        }

        @Nullable
        public MessageChannel getChannel() {
            return channel;
        }

        public long getMessageId() {
            return messageId;
        }
    }

    public static class Builder extends TwoUsersChoicesActionMenu.Builder<Builder, RPSMenu> {
        @Nullable
        private Message start;
        @Nonnull
        private BiConsumer<RPS, ButtonClickEvent> onChoiceMade;
        @Nonnull
        private BiConsumer<RPSResult, ButtonClickEvent> onResult;
        @Nonnull
        private Consumer<RPSTimeoutEvent> onTimeout;

        public Builder() {
            super(Builder.class);

            onChoiceMade = (rps, e) -> {
            };
            onResult = (r, e) -> {
            };
            onTimeout = event -> {
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
        }

        @Nonnull
        public Builder setStart(@Nonnull Message start) {
            this.start = start;
            return this;
        }

        @Nonnull
        public Builder setOnChoiceMade(@Nonnull BiConsumer<RPS, ButtonClickEvent> onChoiceMade) {
            this.onChoiceMade = onChoiceMade;
            return this;
        }

        @Nonnull
        public Builder setOnResult(@Nonnull BiConsumer<RPSResult, ButtonClickEvent> onResult) {
            this.onResult = onResult;
            return this;
        }

        /**
         * MessageChannel may be null on timeout in weird cases
         * <p>
         * Default: look at source lol it's too long for docs: {@link #build()}
         */
        @Nonnull
        public RPSMenu.Builder setOnTimeout(@Nonnull Consumer<RPSTimeoutEvent> onTimeout) {
            this.onTimeout = onTimeout;
            return this;
        }

        @Nullable
        public Message getStart() {
            return start;
        }

        @Nonnull
        public BiConsumer<RPS, ButtonClickEvent> getOnChoiceMade() {
            return onChoiceMade;
        }

        @Nonnull
        public BiConsumer<RPSResult, ButtonClickEvent> getOnResult() {
            return onResult;
        }

        @Nonnull
        public Consumer<RPSTimeoutEvent> getOnTimeout() {
            return onTimeout;
        }

        @Nonnull
        @Override
        public synchronized RPSMenu build() {
            preBuild();
            if (start == null) throw new IllegalStateException("Start must be set");

            // We know because preBuild waiter, user1, user2 and start aren't null
            //noinspection ConstantConditions
            return new RPSMenu(getWaiter(), getUser1(), getUser2(), getTimeout(), getUnit(), onChoiceMade, onResult, start, onTimeout);
        }
    }
}
