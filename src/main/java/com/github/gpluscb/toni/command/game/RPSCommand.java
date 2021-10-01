package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.components.RPSMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class RPSCommand implements Command {
    @Nonnull
    private final EventWaiter waiter;

    public RPSCommand(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        User user1User;
        User user2User;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();
            OneOfTwo<MiscUtil.OneOrTwoUserArgs, MiscUtil.TwoUserArgsErrorType> argResult = MiscUtil.getTwoUserArgs(msg, false);
            MiscUtil.TwoUserArgsErrorType error = argResult.getU().orElse(null);
            if (error != null) {
                String reply;
                switch (error) {
                    case WRONG_NUMBER_ARGS:
                        reply = "You must mention either one or two users.";
                        break;
                    case NOT_USER_MENTION_ARG:
                        reply = "Arguments must be user mentions.";
                        break;
                    case BOT_USER:
                        reply = "This command doesn't support bot or webhook users.";
                        break;
                    case USER_1_EQUALS_USER_2:
                        reply = "I can't have someone rps with themselves, what would that even look like?";
                        break;
                    default:
                        throw new IllegalStateException("Non exhaustive switch over error");
                }

                ctx.reply(reply).queue();
            }

            MiscUtil.OneOrTwoUserArgs users = argResult.getTOrThrow();
            user1User = users.getUser1User();
            user2User = users.getUser2User();
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

        String user1Mention = MiscUtil.mentionUser(user1);
        String user2Mention = MiscUtil.mentionUser(user2);

        Message start = new MessageBuilder(String.format("Alrighty! %s and %s, please click on the button of your choice now. " +
                "You have three (3) minutes!", user1Mention, user2Mention))
                .mentionUsers(user1, user2)
                .build();

        RPSMenu menu = new RPSMenu.Builder()
                .setWaiter(waiter)
                .setUsers(user1, user2)
                .setStart(start)
                .setTimeout(3, TimeUnit.MINUTES)
                .setOnTimeout(timeout -> onTimeout(timeout, user1, user2))
                .setOnResult((result, e) -> onRPSResult(result, e, user1, user2))
                .build();

        context
                .onT(msg -> menu.displayReplying(msg.getMessage()))
                .onU(slash -> menu.displaySlashReplying(slash.getEvent()));
    }

    private void onRPSResult(@Nonnull RPSMenu.RPSResult result, @Nonnull ButtonClickEvent e, long user1, long user2) {
        String user1Mention = MiscUtil.mentionUser(user1);
        String user2Mention = MiscUtil.mentionUser(user2);

        String outcome;
        if (result.getWinner() == RPSMenu.RPSResult.Winner.Tie) {
            outcome = "It's a tie!";
        } else {
            // We know it's not a tie
            //noinspection ConstantConditions
            outcome = String.format("%s won!", MiscUtil.mentionUser(result.getWinnerId()));
        }

        e.reply(String.format("It has been decided! %s chose %s, and %s chose %s. That means %s",
                        user1Mention, result.getChoiceA().getDisplayName(), user2Mention, result.getChoiceB().getDisplayName(), outcome))
                .mentionUsers(user1, user2)
                .queue();

        // TODO: Edit message, remove ActionRows. Only then MESSAGE_HISTORY will be needed I think
    }

    private void onTimeout(@Nonnull RPSMenu.RPSTimeoutEvent timeout, long user1, long user2) {
        MessageChannel channel = timeout.getChannel();
        RPSMenu.RPS choice1 = timeout.getChoiceA();
        RPSMenu.RPS choice2 = timeout.getChoiceB();
        long messageId = timeout.getMessageId();

        if (channel == null) return;

        // TODO: Variable naming
        StringBuilder lazyIdiots = new StringBuilder();
        if (choice1 == null) {
            lazyIdiots.append(MiscUtil.mentionUser(user1));
            if (choice2 == null) lazyIdiots.append(" and ");
        }
        if (choice2 == null) lazyIdiots.append(MiscUtil.mentionUser(user2));

        channel.editMessageById(messageId, String.format("The three (3) minutes are done. Not all of you have given me your choice. Shame on you, %s!", lazyIdiots))
                .mentionUsers(user1, user2)
                .setActionRows()
                .queue();
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
}
