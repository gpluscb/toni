package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomPlayerCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext ctx) {
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < ctx.getArgNum(); i++) {
            User user = ctx.getUserMentionArg(i);
            if (user == null) {
                ctx.reply("Arguments must be user mentions of users in this server.").queue();
                return;
            }

            long userId = user.getIdLong();

            users.add(userId);
        }

        if (users.size() < 2) {
            ctx.reply("You must mention at least two (2) users.").queue();
            return;
        }

        if (users.size() > 8) {
            ctx.reply("You must not mention more than eight (8) users.").queue();
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(users.size());
        long choice = users.get(rngIndex);

        ctx.reply(String.format("I choose you, %s!", MiscUtil.mentionUser(choice))).mentionUsers(choice).queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"randomplayer", "chooseplayer"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Picks a random player out of multiple choices. Usage: `randomplayer <PLAYERS...>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`randomplayer|chooseplayer <PLAYERS...>`\n" +
                "Picks a random player out of the given choices. Useful if you don't want to play rock paper scissors.";
    }
}
