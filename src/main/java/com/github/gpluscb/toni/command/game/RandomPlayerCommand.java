package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;

public class RandomPlayerCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        // T: User id, U: string choice
        List<OneOfTwo<Long, String>> choices = new ArrayList<>();

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            for (int i = 0; i < msg.getArgNum(); i++) {
                User user = msg.getUserMentionArg(i);
                String stringChoice = null;

                if (user == null) stringChoice = msg.getArg(i);

                // Exactly one of these two will be null
                choices.add(new OneOfTwo<>(user, stringChoice).mapT(User::getIdLong));
            }

            if (choices.size() < 2) {
                ctx.reply("You must give at least two (2) choices.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            String choice1 = slash.getOptionNonNull("choice-1").getAsString();
            Matcher matcher1 = Message.MentionType.USER.getPattern().matcher(choice1);
            if (matcher1.matches()) choices.add(OneOfTwo.ofT(Long.parseLong(matcher1.group(1))));
            else choices.add(OneOfTwo.ofU(choice1));

            String choice2 = slash.getOptionNonNull("choice-2").getAsString();
            Matcher matcher2 = Message.MentionType.USER.getPattern().matcher(choice2);
            if (matcher2.matches()) choices.add(OneOfTwo.ofT(Long.parseLong(matcher2.group(1))));
            else choices.add(OneOfTwo.ofU(choice2));
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(choices.size());
        OneOfTwo<Long, String> choice = choices.get(rngIndex);

        choice.map(id ->
                        ctx.reply(String.format("I choose you, %s!", MiscUtil.mentionUser(id))).mentionUsers(id),
                str ->
                        ctx.reply(String.format("I choose %s.", str))
        ).queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"randomplayer", "chooseplayer", "choose"})
                .setShortHelp("Picks a random element out of multiple choices. Usage: `choose <CHOICES...>`")
                .setDetailedHelp("`choose <CHOICES...>`\n" +
                        "Picks a random choice out of the given choices. Useful if you don't want to play rock paper scissors for example.\n" +
                        "The slash command version supports only two (2) choices.\n" +
                        "Aliases: `choose`, `randomplayer`, `chooseplayer`")
                .setCommandData(new CommandData("choose", "Choose between two options")
                        .addOption(OptionType.STRING, "choice-1", "The first choice", true)
                        .addOption(OptionType.STRING, "choice-2", "The second choice", true))
                .build();
    }
}
