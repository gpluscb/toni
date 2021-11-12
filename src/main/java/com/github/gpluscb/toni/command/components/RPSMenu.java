package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.discord.menu.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
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
    private final Consumer<RPSTimeoutEvent> onTimeout;
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
        this.onTimeout = onTimeout;

        underlying = new ButtonActionMenu.Builder()
                .setWaiter(waiter)
                .addUsers(user1, user2)
                .setStart(start)
                .setDeletionButton(null)
                .registerButton(Button.secondary("rock", Emoji.fromUnicode(Constants.ROCK)), e -> choose(e, RPS.ROCK))
                .registerButton(Button.secondary("paper", Emoji.fromUnicode(Constants.PAPER)), e -> choose(e, RPS.PAPER))
                .registerButton(Button.secondary("scissors", Emoji.fromUnicode(Constants.SCISSORS)), e -> choose(e, RPS.SCISSORS))
                .setTimeout(timeout, unit)
                .setTimeoutAction(this::onTimeout)
                .build();
    }

    @Override
    public void display(@Nonnull MessageChannel channel, long messageId) {
        underlying.display(channel, messageId);
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
    private synchronized ButtonActionMenu.MenuAction choose(@Nonnull ButtonClickEvent e, @Nonnull RPS choice) {
        boolean isUser1 = e.getUser().getIdLong() == getUser1();
        if ((isUser1 && choice1 != null) || (!isUser1 && choice2 != null)) {
            e.reply("You have already chosen, and you must learn to live with that choice!")
                    .setEphemeral(true).queue();
            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        onChoiceMade.accept(choice, e);

        if (isUser1) choice1 = choice;
        else choice2 = choice;

        if (choice1 != null && choice2 != null) {
            RPSResult outcome = determineWinner();
            onResult.accept(outcome, e);

            return ButtonActionMenu.MenuAction.CANCEL;
        }

        e.reply("I have noted your choice...").setEphemeral(true).queue();

        return ButtonActionMenu.MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized RPSResult determineWinner() {
        if (choice1 == choice2) return new RPSResult(Winner.Tie);
        Winner winner;
        // At this point choice1 and choice2 will be nonnull
        //noinspection ConstantConditions
        switch (choice1) {
            case ROCK:
                winner = choice2 == RPS.PAPER ? Winner.B : Winner.A;
                break;
            case PAPER:
                winner = choice2 == RPS.SCISSORS ? Winner.B : Winner.A;
                break;
            case SCISSORS:
                winner = choice2 == RPS.ROCK ? Winner.B : Winner.A;
                break;
            default:
                throw new IllegalStateException("Not all RPS options covered");
        }

        return new RPSResult(winner);
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        onTimeout.accept(new RPSTimeoutEvent(choice1, choice2));
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
    }

    public enum Winner {
        A,
        B,
        Tie,
    }

    private abstract class RPSStateInfo extends TwoUsersMenuStateInfo {
        @Nullable
        public RPS getChoice1() {
            return choice1;
        }

        @Nullable
        public RPS getChoice2() {
            return choice2;
        }
    }

    public class RPSResult extends RPSStateInfo {
        @Nonnull
        private final Winner winner;

        public RPSResult(@Nonnull Winner winner) {
            this.winner = winner;
        }

        @Nonnull
        public Winner getWinner() {
            return winner;
        }

        @Nullable
        public Long getWinnerId() {
            switch (winner) {
                case A:
                    return getUser1();
                case B:
                    return getUser2();
                case Tie:
                    return null;
                default:
                    throw new IllegalStateException("Non-exhaustive switch");
            }
        }

        @Nullable
        public Long getLoserId() {
            switch (winner) {
                case A:
                    return getUser2();
                case B:
                    return getUser1();
                case Tie:
                    return null;
                default:
                    throw new IllegalStateException("Non-exhaustive switch");
            }
        }

        @Override
        @Nonnull
        public RPS getChoice1() {
            // Should not be null here
            //noinspection ConstantConditions
            return super.getChoice1();
        }

        @Override
        @Nonnull
        public RPS getChoice2() {
            // Should not be null here
            //noinspection ConstantConditions
            return super.getChoice2();
        }
    }

    public class RPSTimeoutEvent extends TwoUsersMenuStateInfo {
        @Nullable
        private final RPS choiceA;
        @Nullable
        private final RPS choiceB;

        public RPSTimeoutEvent(@Nullable RPS choiceA, @Nullable RPS choiceB) {
            this.choiceA = choiceA;
            this.choiceB = choiceB;
        }

        @Nullable
        public RPS getChoiceA() {
            return choiceA;
        }

        @Nullable
        public RPS getChoiceB() {
            return choiceB;
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
