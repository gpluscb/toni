package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ButtonActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class RockPaperScissorsCommand implements Command {
    @Nonnull
    private final EventWaiter waiter;

    public RockPaperScissorsCommand(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        User user1User;
        User user2User;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            int argNum = msg.getArgNum();
            if (argNum < 1 || argNum > 2) {
                ctx.reply("You must mention either one or two users.").queue();
                return;
            }

            user1User = msg.getUserMentionArg(0);
            user2User = argNum == 2 ? msg.getUserMentionArg(1) : ctx.getUser();
            if (user1User == null || user2User == null) {
                ctx.reply("Arguments must be user mentions of users in this server.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            user1User = slash.getOptionNonNull("player-1").getAsUser();

            OptionMapping user2Option = slash.getOption("player-2");

            user2User = user2Option == null ? ctx.getUser() : user2Option.getAsUser();
        }

        if (user1User.isBot() || user2User.isBot()) {
            ctx.reply("I can't support bot/webhook users right now, sorry.").queue();
            return;
        }

        long user1 = user1User.getIdLong();
        long user2 = user2User.getIdLong();

        if (user1 == user2) {
            ctx.reply("I can't have people play against themselves. How would that even work?").queue();
            return;
        }

        String user1Mention = user1User.getAsMention();
        String user2Mention = user2User.getAsMention();

        Message start = new MessageBuilder(String.format("Alrighty! %s and %s, please click on the button of your choice now. " +
                "You have three (3) minutes!", user1Mention, user2Mention))
                .mentionUsers(user1, user2)
                .build();

        RPSHandler handler = new RPSHandler(user1, user2);

        ButtonActionMenu menu = new ButtonActionMenu.Builder()
                .setEventWaiter(waiter)
                .setDeletionButton(null)
                .addUsers(user1, user2)
                .registerButton(Button.secondary("rock", Emoji.fromUnicode(Constants.ROCK)), handler::rockButton)
                .registerButton(Button.secondary("paper", Emoji.fromUnicode(Constants.PAPER)), handler::paperButton)
                .registerButton(Button.secondary("scissors", Emoji.fromUnicode(Constants.SCISSORS)), handler::scissorsButton)
                .setStart(start)
                .setTimeout(3, TimeUnit.MINUTES)
                .setTimeoutAction(handler::timeout)
                .build();

        context.onT(msg ->
                        menu.displayReplying(msg.getMessage()))
                .onU(slash ->
                        menu.displaySlashCommandReplying(slash.getEvent()));
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY})
                .setAliases(new String[]{"rps", "rockpaperscissors"})
                .setShortHelp("Helps you play rock paper scissors. Usage: `rps [PLAYER 1] <PLAYER 2>`")
                .setDetailedHelp("`rps [PLAYER 1 (default: message author)] <PLAYER 2>`\n" +
                        "Helps you play the world famous game of [rock paper scissors](https://en.wikipedia.org/wiki/Rock_paper_scissors). " +
                        "After performing the command, both participants will have to DM me. " +
                        "So you might have to unblock me (but what kind of monster would have me blocked in the first place?)\n" +
                        "Aliases: `rockpaperscissors`, `rps`")
                .setCommandData(new CommandData("rps", "Helps you play rock paper scissors")
                        .addOption(OptionType.USER, "player-1", "The first rps player", true)
                        .addOption(OptionType.USER, "player-2", "The second rps player. This is yourself by default", false))
                .build();
    }

    private static class RPSHandler {
        private boolean finished;
        private final long user1;
        private final long user2;
        @Nullable
        private RPS choice1;
        @Nullable
        private RPS choice2;

        private RPSHandler(long user1, long user2) {
            finished = false;
            this.user1 = user1;
            this.user2 = user2;
        }

        @Nullable
        public synchronized Message rockButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.ROCK);
            return null;
        }

        @Nullable
        public synchronized Message paperButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.PAPER);
            return null;
        }

        @Nullable
        public synchronized Message scissorsButton(@Nonnull ButtonClickEvent e) {
            choose(e, e.getUser().getIdLong() == user1, RPS.SCISSORS);
            return null;
        }

        private synchronized void choose(@Nonnull ButtonClickEvent e, boolean isUser1, @Nonnull RPS choice) {
            if (finished) return;

            if ((isUser1 && choice1 != null) || (!isUser1 && choice2 != null)) {
                e.reply("You have already chosen, and you must learn to live with that choice!")
                        .setEphemeral(true).queue();
                return;
            }

            if (isUser1) choice1 = choice;
            else choice2 = choice;

            if (choice1 != null && choice2 != null) {
                String user1Mention = MiscUtil.mentionUser(user1);
                String user2Mention = MiscUtil.mentionUser(user2);

                int intOutcome = RPS.determineOutcome(choice1, choice2);
                String outcome = intOutcome == 0 ? "It's a tie!"
                        : String.format("%s won!", intOutcome > 0 ? user1Mention : user2Mention);

                e.reply(String.format("It has been decided! %s chose %s, and %s chose %s. That means %s",
                                user1Mention, choice1.getName(), user2Mention, choice2.getName(), outcome))
                        .mentionUsers(user1, user2)
                        .queue();

                Message originalMessage = e.getMessage();
                originalMessage.editMessage(originalMessage).setActionRows().queue();

                finished = true;
            } else {
                e.reply("I have noted your choice...").setEphemeral(true).queue();
            }
        }

        public synchronized void timeout(@Nullable MessageChannel channel, long messageId) {
            if (channel == null) return;

            // TODO: Variable naming
            StringBuilder lazyIdiots = new StringBuilder();
            if (choice1 == null) {
                lazyIdiots.append(MiscUtil.mentionUser(user1));
                if (choice2 == null) lazyIdiots.append(" and ");
            }
            if (choice2 == null) lazyIdiots.append(MiscUtil.mentionUser(user2));

            channel.sendMessage(String.format("The three (3) minutes are done. Not all of you have given me your choice. Shame on you, %s!", lazyIdiots)).mentionUsers(user1, user2)
                    .queue();

            channel.retrieveMessageById(messageId).flatMap(m -> m.editMessage(m).setActionRows()).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        }
    }

    private enum RPS {
        ROCK,
        PAPER,
        SCISSORS;

        @Nonnull
        private String getName() {
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

        /**
         * 1: a wins
         * 0: tie
         * -1: b wins
         */
        public static int determineOutcome(@Nonnull RPS a, @Nonnull RPS b) {
            if (a == b) return 0;
            switch (a) {
                case ROCK:
                    return b == PAPER ? -1 : 1;
                case PAPER:
                    return b == SCISSORS ? -1 : 1;
                case SCISSORS:
                    return b == ROCK ? -1 : 1;
            }
            return 0;
        }
    }
}
