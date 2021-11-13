package com.github.gpluscb.toni.command.components;

import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.menu.ButtonActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RPSMenu extends TwoUsersChoicesActionMenu {
    @Nonnull
    private final Settings settings;

    @Nonnull
    private final ButtonActionMenu underlying;

    @Nullable
    private RPS choice1;
    @Nullable
    private RPS choice2;

    public RPSMenu(@Nonnull Settings settings) {
        super(settings.twoUsersChoicesActionMenuSettings());
        this.settings = settings;

        underlying = new ButtonActionMenu(new ButtonActionMenu.Settings.Builder()
                .setActionMenuSettings(getActionMenuSettings())
                .addUsers(getTwoUsersChoicesActionMenuSettings().user1(), getTwoUsersChoicesActionMenuSettings().user2())
                .setStart(settings.start())
                .setDeletionButton(null)
                .registerButton(Button.secondary("rock", Emoji.fromUnicode(Constants.ROCK)), e -> choose(e, RPS.ROCK))
                .registerButton(Button.secondary("paper", Emoji.fromUnicode(Constants.PAPER)), e -> choose(e, RPS.PAPER))
                .registerButton(Button.secondary("scissors", Emoji.fromUnicode(Constants.SCISSORS)), e -> choose(e, RPS.SCISSORS))
                .setOnTimeout(this::onTimeout)
                .build());
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
        boolean isUser1 = e.getUser().getIdLong() == getTwoUsersChoicesActionMenuSettings().user1();
        if ((isUser1 && choice1 != null) || (!isUser1 && choice2 != null)) {
            e.reply("You have already chosen, and you must learn to live with that choice!")
                    .setEphemeral(true).queue();
            return ButtonActionMenu.MenuAction.CONTINUE;
        }

        settings.onChoiceMade().accept(choice, e);

        if (isUser1) choice1 = choice;
        else choice2 = choice;

        if (choice1 != null && choice2 != null) {
            RPSResult outcome = determineWinner();
            settings.onResult().accept(outcome, e);

            return ButtonActionMenu.MenuAction.CANCEL;
        }

        e.reply("I have noted your choice...").setEphemeral(true).queue();

        return ButtonActionMenu.MenuAction.CONTINUE;
    }

    @Nonnull
    private synchronized RPSResult determineWinner() {
        if (choice1 == choice2) return new RPSResult(Winner.Tie);

        // At this point choice1 and choice2 will be nonnull
        //noinspection ConstantConditions
        Winner winner = switch (choice1) {
            case ROCK -> choice2 == RPS.PAPER ? Winner.B : Winner.A;
            case PAPER -> choice2 == RPS.SCISSORS ? Winner.B : Winner.A;
            case SCISSORS -> choice2 == RPS.ROCK ? Winner.B : Winner.A;
        };

        return new RPSResult(winner);
    }

    private synchronized void onTimeout(@Nonnull ButtonActionMenu.ButtonActionMenuTimeoutEvent event) {
        settings.onTimeout().accept(new RPSTimeoutEvent(choice1, choice2));
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
            return switch (this) {
                case ROCK -> Constants.ROCK + "(rock)";
                case PAPER -> Constants.PAPER + "(paper)";
                case SCISSORS -> Constants.SCISSORS + "(scissors)";
            };
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
            return switch (winner) {
                case A -> getTwoUsersChoicesActionMenuSettings().user1();
                case B -> getTwoUsersChoicesActionMenuSettings().user2();
                case Tie -> null;
            };
        }

        @Nullable
        public Long getLoserId() {
            return switch (winner) {
                case A -> getTwoUsersChoicesActionMenuSettings().user2();
                case B -> getTwoUsersChoicesActionMenuSettings().user1();
                case Tie -> null;
            };
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

    public record Settings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings,
                           @Nonnull BiConsumer<RPS, ButtonClickEvent> onChoiceMade,
                           @Nonnull BiConsumer<RPSResult, ButtonClickEvent> onResult, @Nonnull Message start,
                           @Nonnull Consumer<RPSTimeoutEvent> onTimeout) {
        @Nonnull
        public static final BiConsumer<RPS, ButtonClickEvent> DEFAULT_ON_CHOICE_MADE = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final BiConsumer<RPSResult, ButtonClickEvent> DEFAULT_ON_RESULT = MiscUtil.emptyBiConsumer();
        @Nonnull
        public static final Consumer<RPSTimeoutEvent> DEFAULT_ON_TIMEOUT = event -> {
            MessageChannel channel = event.getChannel();
            long id = event.getMessageId();
            if (channel == null) return;
            if (channel instanceof TextChannel textChannel) {
                if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                    return;
            }

            channel.retrieveMessageById(id)
                    .flatMap(m -> m.editMessage(m).setActionRows())
                    .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        };
        ;

        public static class Builder {
            @Nullable
            private TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings;
            @Nullable
            private Message start;
            @Nonnull
            private BiConsumer<RPS, ButtonClickEvent> onChoiceMade = DEFAULT_ON_CHOICE_MADE;
            @Nonnull
            private BiConsumer<RPSResult, ButtonClickEvent> onResult = DEFAULT_ON_RESULT;
            @Nonnull
            private Consumer<RPSTimeoutEvent> onTimeout = DEFAULT_ON_TIMEOUT;

            public Builder setTwoUsersChoicesActionMenuSettings(@Nonnull TwoUsersChoicesActionMenu.Settings twoUsersChoicesActionMenuSettings) {
                this.twoUsersChoicesActionMenuSettings = twoUsersChoicesActionMenuSettings;
                return this;
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

            @Nonnull
            public Builder setOnTimeout(@Nonnull Consumer<RPSTimeoutEvent> onTimeout) {
                this.onTimeout = onTimeout;
                return this;
            }

            @Nonnull
            public synchronized Settings build() {
                if (twoUsersChoicesActionMenuSettings == null)
                    throw new IllegalStateException("TwoUsersChoicesActionMEnuSettings must be set");
                if (start == null) throw new IllegalStateException("Start must be set");

                return new Settings(twoUsersChoicesActionMenuSettings, onChoiceMade, onResult, start, onTimeout);
            }
        }
    }
}
