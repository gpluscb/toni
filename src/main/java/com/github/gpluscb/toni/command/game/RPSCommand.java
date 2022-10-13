package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.command.menu.RPSMenu;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ClassCanBeRecord")
public class RPSCommand implements Command {
    @Nonnull
    private final EventWaiter waiter;

    public RPSCommand(@Nonnull EventWaiter waiter) {
        this.waiter = waiter;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {

        User user1User = ctx.getOptionNonNull("player-1").getAsUser();

        OptionMapping user2Option = ctx.getOption("player-2");

        User user2User = user2Option == null ? ctx.getUser() : user2Option.getAsUser();

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

        RPSMenu menu = new RPSMenu(new RPSMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(waiter)
                                .setTimeout(3, TimeUnit.MINUTES)
                                .build())
                        .setUsers(user1, user2)
                        .build())
                .setStart(start)
                .setOnTimeout(timeout -> onTimeout(timeout, user1, user2))
                .setOnResult((result, e) -> onRPSResult(result, e, user1, user2))
                .build());

        menu.displaySlashReplying(ctx.getEvent());
    }

    private void onRPSResult(@Nonnull RPSMenu.RPSResult result, @Nonnull ButtonInteractionEvent e, long user1, long user2) {
        String user1Mention = MiscUtil.mentionUser(user1);
        String user2Mention = MiscUtil.mentionUser(user2);

        String outcome;
        if (result.getWinner() == RPSMenu.Winner.Tie) {
            outcome = "It's a tie!";
        } else {
            // We know it's not a tie
            //noinspection ConstantConditions
            outcome = String.format("%s won!", MiscUtil.mentionUser(result.getWinnerId()));
        }

        e.reply(String.format("It has been decided! %s chose %s, and %s chose %s. That means %s",
                        user1Mention, result.getChoice1().getDisplayName(), user2Mention, result.getChoice2().getDisplayName(), outcome))
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
                .setShortHelp("Helps you play rock paper scissors.")
                .setDetailedHelp("""
                        Helps you play the world famous game of [rock paper scissors](https://en.wikipedia.org/wiki/Rock_paper_scissors).
                        Slash command options:
                        • `player-1`: The first rps contestant.
                        • (Optional) `player-2`: The second rps contestant. This is yourself by default.""")
                .setCommandData(Commands.slash("rps", "Helps you play rock paper scissors")
                        .addOption(OptionType.USER, "player-1", "The first rps player", true)
                        .addOption(OptionType.USER, "player-2", "The second rps player. This is yourself by default", false))
                .build();
    }
}
