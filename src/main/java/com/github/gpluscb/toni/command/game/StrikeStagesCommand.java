package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.components.StrikeStagesComponent;
import com.github.gpluscb.toni.util.smash.Ruleset;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class StrikeStagesCommand implements Command {
    @Nonnull
    private final StrikeStagesComponent component;
    @Nonnull
    private final List<Ruleset> rulesets;

    public StrikeStagesCommand(@Nonnull StrikeStagesComponent component, @Nonnull List<Ruleset> rulesets) {
        this.component = component;
        this.rulesets = rulesets;
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

        ctx.reply(String.format("Please strike stages. %s starts.", user1Mention))
                .mentionUsers(user1)
                .queue(m -> component.attachStageStriking(m, rulesets.get(0), user1, user2));
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"strike", "strikestarters"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return null;
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return null;
    }
}
