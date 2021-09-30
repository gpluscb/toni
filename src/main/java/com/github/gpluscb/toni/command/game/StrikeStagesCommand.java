package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.components.RPSComponent;
import com.github.gpluscb.toni.command.components.StrikeStagesComponent;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

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
    public void execute(@Nonnull CommandContext<?> ctx) {
        long user1;
        long user2;
        boolean doRPS = false;
        // TODO: Server default ruleset (and maybe even server default doRPS setting?)
        Ruleset ruleset = rulesets.get(0);

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            OneOfTwo<MiscUtil.OneOrTwoUserArgs, MiscUtil.TwoUserArgsErrorType> result = MiscUtil.getTwoUserArgs(msg, true);

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
                return;
            }

            MiscUtil.OneOrTwoUserArgs users = result.getTOrThrow();
            user1 = users.getUser1();
            user2 = users.getUser2();

            int continuedArgsIdx = users.isTwoArgumentsGiven() ? 2 : 1;
            int argNum = msg.getArgNum();

            if (argNum == continuedArgsIdx + 1) {
                String eitherString = msg.getArg(continuedArgsIdx);
                Boolean doRPSNullable = MiscUtil.boolFromString(eitherString);
                if (doRPSNullable == null) {
                    // TODO: Dupe code
                    try {
                        int rulesetId = Integer.parseInt(eitherString);
                        ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.getRulesetId() == rulesetId).findAny().orElse(null);
                        if (ruleset == null) {
                            // TODO: Ruleset list command
                            ctx.reply("The given ruleset id is invalid.").queue();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        ctx.reply("A given argument is invalid. It must either be a ruleset id, or true, or false. Use `toni, help strike` for details.").queue();
                        return;
                    }
                } else {
                    doRPS = doRPSNullable;
                }
            } else if (argNum == continuedArgsIdx + 2) {
                String rulesetIdString = msg.getArg(continuedArgsIdx);
                try {
                    int rulesetId = Integer.parseInt(rulesetIdString);
                    ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.getRulesetId() == rulesetId).findAny().orElse(null);
                    if (ruleset == null) {
                        // TODO: Ruleset list command
                        ctx.reply("The given ruleset id is invalid.").queue();
                        return;
                    }
                } catch (NumberFormatException e) {
                    ctx.reply("The ruleset id must be an integer. Use `toni, help strike` for details.").queue();
                    return;
                }

                String doRPSString = msg.getArg(continuedArgsIdx + 1);
                Boolean doRPSNullable = MiscUtil.boolFromString(doRPSString);
                if (doRPSNullable == null) {
                    ctx.reply("The indication whether to do RPS must be either `true` or `false`. Use `toni, help strike` for details.").queue();
                    return;
                }

                doRPS = doRPSNullable;
            } else if (argNum > continuedArgsIdx + 2) {
                ctx.reply("You gave too many arguments. Use `toni, help strike` for details.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            user1 = slash.getOptionNonNull("striker-1").getAsUser().getIdLong();

            OptionMapping user2Mapping = slash.getOption("striker-2");
            user2 = (user2Mapping == null ? ctx.getUser() : user2Mapping.getAsUser())
                    .getIdLong();

            OptionMapping rulesetIdMapping = slash.getOption("ruleset-id");
            if (rulesetIdMapping != null) {
                long rulesetId = rulesetIdMapping.getAsLong();
                ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.getRulesetId() == rulesetId).findAny().orElse(null);
                if (ruleset == null) {
                    ctx.reply("The given ruleset id is invalid.").queue();
                    return;
                }
            }

            OptionMapping doRpsMapping = slash.getOption("rps");
            doRPS = doRpsMapping != null && doRpsMapping.getAsBoolean();
        }

        int[] starterStrikePattern = ruleset.getStarterStrikePattern();
        if (starterStrikePattern.length == 0) {
            // Has exactly one element in this case
            Stage stage = ruleset.getStarters().get(0);
            ctx.reply(String.format("This ruleset only has one starter weirdly. You're going to ~~Brazil~~ %s.", stage.getName())).queue();
        }

        boolean doRPS_ = doRPS;
        Ruleset ruleset_ = ruleset;
        context.map(
                msg -> component.sendStageStrikingReplying(msg.getMessage(), ruleset_, user1, user2, doRPS_),
                slash -> component.sendSlashStageStrikingReplying(slash.getEvent(), ruleset_, user1, user2, doRPS_)
        ).whenComplete(FailLogger.logFail((pair, timeout) -> {
            if (timeout != null) {
                if (timeout instanceof CompletionException) {
                    Throwable timeoutCause = timeout.getCause();

                    if (timeoutCause instanceof StrikeStagesComponent.StrikeStagesTimeoutException) {
                        StrikeStagesComponent.StrikeStagesTimeoutException strikeStagesTimeout = (StrikeStagesComponent.StrikeStagesTimeoutException) timeoutCause;
                        long badUser = strikeStagesTimeout.getCurrentStriker();
                        MessageChannel channel = strikeStagesTimeout.getChannel();
                        if (channel == null) return;
                        long messageId = strikeStagesTimeout.getMessageId();

                        channel.editMessageById(messageId, String.format("%s, you didn't strike the stage in time.", MiscUtil.mentionUser(badUser)))
                                .mentionUsers(badUser)
                                .setActionRows()
                                .queue();

                        return;
                    } else if (timeoutCause instanceof StrikeStagesComponent.ChooseFirstStrikerTimeoutException) {
                        StrikeStagesComponent.ChooseFirstStrikerTimeoutException firstStrikerTimeout = (StrikeStagesComponent.ChooseFirstStrikerTimeoutException) timeoutCause;
                        long badUser = firstStrikerTimeout.getUser();
                        MessageChannel channel = firstStrikerTimeout.getChannel();
                        if (channel == null) return;
                        long messageId = firstStrikerTimeout.getMessageId();

                        channel.editMessageById(messageId, String.format("%s, you didn't choose if you want to strike first in time.", MiscUtil.mentionUser(badUser)))
                                .mentionUsers(badUser)
                                .setActionRows()
                                .queue();

                        return;
                    } else if (timeoutCause instanceof RPSComponent.RPSTimeoutException) {
                        RPSComponent.RPSTimeoutException rpsTimeout = (RPSComponent.RPSTimeoutException) timeoutCause;

                        MessageChannel channel = rpsTimeout.getChannel();
                        if (channel == null) return;
                        long messageId = rpsTimeout.getMessageId();

                        RPSComponent.RPS choice1 = rpsTimeout.getChoiceA();
                        RPSComponent.RPS choice2 = rpsTimeout.getChoiceB();

                        StringBuilder lazyIdiots = new StringBuilder();
                        if (choice1 == null) {
                            lazyIdiots.append(MiscUtil.mentionUser(user1));
                            if (choice2 == null) lazyIdiots.append(" and ");
                        }
                        if (choice2 == null) lazyIdiots.append(MiscUtil.mentionUser(user2));

                        channel.editMessageById(messageId, String.format("You didn't all complete the RPS! Be more alert next time, %s.", lazyIdiots))
                                .mentionUsers(user1, user2)
                                .setActionRows()
                                .queue();

                        return;
                    }
                }

                log.error("Failed StrikeStages completion not StrikeStagesTimeoutException", timeout);
                // TODO: Tell the user
                return;
            }

            List<Set<Integer>> strikes = pair.getT();
            ButtonClickEvent e = pair.getU();

            Stage resultingStage = ruleset_.getStarters().stream()
                    .filter(starter -> strikes.stream().noneMatch(struckStarters -> struckStarters.contains(starter.getStageId())))
                    .findAny()
                    .orElse(null);

            if (resultingStage == null) {
                log.error("All starters have been struck. Strikes: {}. Ruleset id: {}", strikes, ruleset_.getRulesetId());
                e.editMessage("Somehow you have struck all starters. This is a bug. I've told my dev about it but you should give them some context too.").queue();
                return;
            }

            e.editMessage(String.format("You have struck to %s.", resultingStage.getName())).setActionRows().queue();
        }));
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY})
                .setAliases(new String[]{"strike", "strikestarters", "strikestages"})
                .setShortHelp("Helps you do the stage striking procedure with a specific ruleset. Usage: `strike [PLAYER 1] <PLAYER 2> [RULESET ID] [DO RPS]`")
                // TODO If we get passed ctx here, we can actually name the server default ruleset
                .setDetailedHelp("`strike [PLAYER 1] <PLAYER 2> [RULESET ID (default: server default ruleset)] [DO RPS (true|false(default))]`\n" +
                        "Helps you perform the [stage striking procedure](https://www.ssbwiki.com/Stage_striking) for a given ruleset. " +
                        "Depending on the `DO RPS` argument, you'll play a game of RPS first to determine who gets to strike first.\n" +
                        "For a list of rulesets and their IDs, use the `rulesets` command.\n" +
                        "Aliases: `strike`, `strikestarters`, `strikestages`")
                .setCommandData(new CommandData("strikestarters", "Helps you perform the stage striking procedure")
                        .addOption(OptionType.USER, "striker-1", "One participant in the striking procedure", true)
                        .addOption(OptionType.USER, "striker-2", "The other participant in the striking procedure. This is yourself by default", false)
                        // TODO: Only allow valid ids
                        .addOption(OptionType.INTEGER, "ruleset-id", "The id of the ruleset with the starter stage list", false) // TODO: Use "listrulesets" for a list of rulesets or w/e. Also maybe server default ruleset?
                        .addOption(OptionType.BOOLEAN, "rps", "Whether to play RPS for who strikes first. By default the first striker is determined randomly", false))
                .build();
    }
}
