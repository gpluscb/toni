package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;

public class RandomPlayerCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext ctx) {
        // T: User id, U: string choice
        List<OneOfTwo<Long, String>> choices = new ArrayList<>();

        String choice1 = ctx.getOptionNonNull("choice-1").getAsString();
        Matcher matcher1 = Message.MentionType.USER.getPattern().matcher(choice1);
        if (matcher1.matches()) choices.add(OneOfTwo.ofT(Long.parseLong(matcher1.group(1))));
        else choices.add(OneOfTwo.ofU(choice1));

        String choice2 = ctx.getOptionNonNull("choice-2").getAsString();
        Matcher matcher2 = Message.MentionType.USER.getPattern().matcher(choice2);
        if (matcher2.matches()) choices.add(OneOfTwo.ofT(Long.parseLong(matcher2.group(1))));
        else choices.add(OneOfTwo.ofU(choice2));

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(choices.size());
        OneOfTwo<Long, String> choice = choices.get(rngIndex);

        // FIXME: I hate Java, how do I do like RestAction<?> + AllowedMentions or sth like that
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
                .setShortHelp("Picks a random element out of multiple choices. Usage: `choose <CHOICES...>`")
                .setDetailedHelp("""
                        Picks a random choice out of the given choices. Useful if you don't want to play rock paper scissors for example.
                        Slash command options:
                        • `choice-1`: The first choice.
                        • `choice-2`: The second choice.""")
                .setCommandData(Commands.slash("choose", "Choose between two options")
                        .addOption(OptionType.STRING, "choice-1", "The first choice", true)
                        .addOption(OptionType.STRING, "choice-2", "The second choice", true))
                .build();
    }
}
