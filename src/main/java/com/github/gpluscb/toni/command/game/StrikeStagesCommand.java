package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.components.StrikeStagesComponent;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class StrikeStagesCommand implements Command {
    private static final Logger log = LogManager.getLogger(StrikeStagesCommand.class);

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
        // TODO: Ruleset selection (standard ruleset per server??)
        // TODO: Full stage striking (includes RPS)

        // TODO: Move this code to MiscUtil or sth like that (useful in RPS and probably in doubleblind as well)
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

        Ruleset ruleset = rulesets.get(0);

        // TODO: Technically, rulesets with only one starter and 0 sized starterStrikePattern are legal
        Message message = new MessageBuilder(
                String.format("It is time to strike stages. %s, you begin by striking %d stages.",
                        MiscUtil.mentionUser(user1),
                        ruleset.getStarterStrikePattern()[0])
        ).mentionUsers(user1).build();

        component.sendStageStrikingReplying(ctx.getMessage(), message, ruleset, user1, user2).whenComplete(FailLogger.logFail((pair, timeout) -> {
            if (timeout != null) {
                // TODO
                return;
            }

            List<Set<Integer>> strikes = pair.getT();
            ButtonClickEvent e = pair.getU();

            Stage resultingStage = ruleset.getStarters().stream()
                    .filter(starter -> strikes.stream().noneMatch(struckStarters -> struckStarters.contains(starter.getStageId())))
                    .findAny()
                    .orElse(null);

            if (resultingStage == null) {
                log.error("All starters have been struck. Strikes: {}. Ruleset id: {}", strikes, ruleset.getRulesetId());
                e.editMessage("Somehow you have struck all starters. This is a bug. I've told my dev about it but you should give them some context too.").queue();
                return;
            }

            e.editMessage(String.format("You have struck to %s.", resultingStage.getName())).setActionRows().queue();
        }));
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"strike", "strikestarters"};
    }

    // TODO
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
