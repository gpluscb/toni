package com.github.gpluscb.smashggnotifications.command.game;

import com.github.gpluscb.smashggnotifications.command.Command;
import com.github.gpluscb.smashggnotifications.command.CommandContext;
import com.github.gpluscb.smashggnotifications.util.DMChoiceWaiter;
import com.github.gpluscb.smashggnotifications.util.FailLogger;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RockPaperScissorsCommand implements Command {
    @Nonnull
    private final DMChoiceWaiter waiter;

    public RockPaperScissorsCommand(@Nonnull DMChoiceWaiter waiter) {
        this.waiter = waiter;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        int argNum = ctx.getArgNum();
        if (ctx.getArgNum() < 1 || ctx.getArgNum() > 2) {
            ctx.reply("You must mention either one or two users.").queue();
            return;
        }

        User user1User = ctx.getUserMentionArg(0);
        User user2User = argNum == 2 ? ctx.getUserMentionArg(1) : ctx.getAuthor();
        if (user1User == null || user2User == null) {
            ctx.reply("Arguments must be user mentions of users in this server.").queue();
            return;
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

        // TODO: Avoid too many mentions somehow -> could potentially be a vector for ping spams
        List<Long> users = new ArrayList<>();
        users.add(user1);
        users.add(user2);

        boolean worked = waiter.waitForDMChoice(users, true, e -> {
            String choice = e.getMessage().getContentRaw();

            RPS rpsChoice = RPS.fromString(choice);
            if (rpsChoice == null)
                e.getChannel().sendMessage("What is that supposed to mean? I only know rock, paper, and scissors.").queue();
            else e.getChannel().sendMessage("Noted!").queue();

            return Optional.ofNullable(rpsChoice);
        }, map -> {
            RPS choice1 = map.get(user1);
            RPS choice2 = map.get(user2);

            int intOutcome = RPS.determineOutcome(choice1, choice2);
            String outcome = intOutcome == 0 ? "It's a tie!"
                    : String.format("%s won!", intOutcome > 0 ? user1Mention : user2Mention);

            ctx.reply(String.format("It has been decided! %s chose %s, and %s chose %s. That means %s", user1Mention, choice1.getName(), user2Mention, choice2.getName(), outcome)).mentionUsers(user1, user2).queue();
        }, 3, TimeUnit.MINUTES, FailLogger.logFail(map -> { // Only timeoutAction on non-WS thread, so this wouldn't log otherwise
            // TODO: Variable naming
            StringBuilder lazyIdiots = new StringBuilder();
            RPS choice1 = map.get(user1);
            RPS choice2 = map.get(user2);
            if (choice1 == null) {
                lazyIdiots.append(user1Mention);
                if (choice2 == null) lazyIdiots.append(" and ");
            }
            if (choice2 == null) lazyIdiots.append(user2Mention);

            ctx.reply(String.format("The three (3) minutes are done. Not all of you have given me your choice. Shame on you, %s!", lazyIdiots.toString())).mentionUsers(user1, user2).queue();
        }));

        if (worked) // TODO: Trusts that this message will go through. Not that big an issue, but still iffy. Optimally you would have something to cancel the thing?
            ctx.reply(String.format("Alrighty! %s and %s, please send me a DM with your choice now. You have three (3) minutes!", user1Mention, user2Mention)).mentionUsers(user1, user2).queue();
        else
            ctx.reply("Some of you ppl are already doing a DM thing with me. If I let you do rock paper scissors, I won't know what you want to tell me in DMs!").queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"rps", "rockpaperscissors"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Helps you play rock paper scissors. Usage: `rps [PLAYER 1] <PLAYER 2>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`rps|rockpaperscissors [PLAYER 1 (default: message author)] <PLAYER 2>`\n" +
                "Helps you play the world famous game of [rock paper scissors](https://en.wikipedia.org/wiki/Rock_paper_scissors). " +
                "After performing the command, both participants will have to DM me. " +
                "So you might have to unblock me (but what kind of monster would have me blocked in the first place?)";
    }

    private enum RPS {
        ROCK,
        PAPER,
        SCISSORS;

        @Nonnull
        private String getName() {
            switch (this) {
                case ROCK:
                    return "rock";
                case PAPER:
                    return "paper";
                case SCISSORS:
                    return "scissors";
                default:
                    return null;
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

        @Nullable
        public static RPS fromString(@Nonnull String string) {
            switch (string.toLowerCase()) {
                case "rock":
                case "r":
                    return ROCK;
                case "paper":
                case "p":
                    return PAPER;
                case "scissors":
                case "scissor":
                case "s":
                    return SCISSORS;
                default:
                    return null;
            }
        }
    }
}
