package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomPlayerCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext ctx) {
        // T: User id, U: string choice
        List<OneOfTwo<Long, String>> choices = new ArrayList<>();
        for (int i = 0; i < ctx.getArgNum(); i++) {
            User user = ctx.getUserMentionArg(i);
            String stringChoice = null;

            if (user == null) stringChoice = ctx.getArg(i);

            // Exactly one of these two will be null
            choices.add(new OneOfTwo<>(user, stringChoice).mapT(User::getIdLong));
        }

        if (choices.size() < 2) {
            ctx.reply("You must give at least two (2) choices.").queue();
            return;
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
    public String[] getAliases() {
        return new String[]{"randomplayer", "chooseplayer", "choose"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Picks a random element out of multiple choices. Usage: `choose <CHOICES...>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`randomplayer|chooseplayer|choose <CHOICES...>`\n" +
                "Picks a random choice out of the given choices. Useful if you don't want to play rock paper scissors for example.";
    }
}
