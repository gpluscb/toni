package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.components.StrikeStagesComponent;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
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

        OneOfTwo<PairNonnull<Long, Long>, MiscUtil.TwoUserArgsErrorType> result = MiscUtil.getTwoUserArgs(ctx, true);

        MiscUtil.TwoUserArgsErrorType error = result.getU().orElse(null);
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
                    reply = "I can't have someone strike with themselves, what would that even look like?";
                    break;
                default:
                    throw new IllegalStateException("Non exhaustive switch over error");
            }

            ctx.reply(reply).queue();
        }

        PairNonnull<Long, Long> users = result.getTOrThrow();
        long user1 = users.getT();
        long user2 = users.getU();

        Ruleset ruleset = rulesets.get(0);

        int[] starterStrikePattern = ruleset.getStarterStrikePattern();
        if (starterStrikePattern.length == 0) {
            // Has exactly one element in this case
            Stage stage = ruleset.getStarters().get(0);
            ctx.reply(String.format("This ruleset only has one starter weirdly. You're going to ~~Brazil~~ %s.", stage)).queue();
        }

        int firstStrike = starterStrikePattern[0];
        Message message = new MessageBuilder(
                String.format("Alright, time to strike stages. %s, you begin by striking %d stage%s.",
                        MiscUtil.mentionUser(user1),
                        firstStrike,
                        firstStrike > 1 ? "s" : "")
        ).mentionUsers(user1).build();

        component.sendStageStrikingReplying(ctx.getMessage(), message, ruleset, user1, user2).whenComplete(FailLogger.logFail((pair, timeout) -> {
            if (timeout != null) {
                if (!(timeout instanceof StrikeStagesComponent.StrikeStagesTimeoutException)) {
                    log.error("Failed StrikeStages completion not StrikeStagesTimeoutException", timeout);
                    // TODO: Tell the user
                    return;
                }

                StrikeStagesComponent.StrikeStagesTimeoutException strikeStagesTimeout = (StrikeStagesComponent.StrikeStagesTimeoutException) timeout;
                long badUser = strikeStagesTimeout.getCurrentStriker();
                MessageChannel channel = strikeStagesTimeout.getChannel();
                if (channel == null) return;
                long messageId = strikeStagesTimeout.getMessageId();

                channel.editMessageById(messageId, String.format("%s, you didn't strike the stage in time.", MiscUtil.mentionUser(badUser)))
                        .mentionUsers(badUser)
                        .setActionRows()
                        .queue();

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
        return new String[]{"strike", "strikestarters", "strikestages"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        // TODO: Actually implement ruleset id and do rps
        return "Helps you do the stage striking procedure with a specific ruleset. Usage: `strike [PLAYER 1] <PLAYER 2> [RULESET ID] [DO RPS]`";
    }

    // TODO
    @Nullable
    @Override
    public String getDetailedHelp() {
        return null;
    }
}
