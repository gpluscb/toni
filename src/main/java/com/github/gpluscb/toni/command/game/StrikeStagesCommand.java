package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.components.RPSAndStrikeStagesMenu;
import com.github.gpluscb.toni.command.components.RPSMenu;
import com.github.gpluscb.toni.command.components.StrikeStagesMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ActionMenu;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class StrikeStagesCommand implements Command {
    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<Ruleset> rulesets;

    public StrikeStagesCommand(@Nonnull EventWaiter waiter, @Nonnull List<Ruleset> rulesets) {
        this.waiter = waiter;
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
            ctx.reply(String.format("This ruleset only has one starter weirdly. You're going to ~~Brazil~~ %s.", stage.getDisplayName())).queue();
        }

        ActionMenu menu;
        if (doRPS) {
            Message start = new MessageBuilder(String.format(
                    "%s and %s, to figure out who strikes first, you will first play RPS.",
                    MiscUtil.mentionUser(user1),
                    MiscUtil.mentionUser(user2)
            )).mentionUsers(user1, user2).build();

            menu = new RPSAndStrikeStagesMenu.Builder()
                    .setWaiter(waiter)
                    .setRuleset(ruleset)
                    .setUsers(user1, user2)
                    .setStart(start)
                    .setOnStrikeResult(this::onStrikeResult)
                    .setOnRPSTimeout(this::onRPSTimeout)
                    .setOnStrikeFirstTimeout(this::onStrikeFirstChoiceTimeout)
                    .setOnStrikeTimeout(this::onStrikeTimeout)
                    .build();
        } else {
            if (ThreadLocalRandom.current().nextBoolean()) {
                // Randomly swap
                long tmp = user1;
                user1 = user2;
                user2 = tmp;
            }

            menu = new StrikeStagesMenu.Builder()
                    .setWaiter(waiter)
                    .setRuleset(ruleset)
                    .setUsers(user1, user2)
                    .setOnResult(this::onStrikeResult)
                    .setOnTimeout(this::onStrikeTimeout)
                    .build();
        }

        context
                .onT(msg -> menu.displayReplying(msg.getMessage()))
                .onU(slash -> menu.displaySlashReplying(slash.getEvent()));
    }

    private void onRPSTimeout(@Nonnull RPSMenu.RPSTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long user1 = timeout.getUser1();
        long user2 = timeout.getUser2();

        RPSMenu.RPS choice1 = timeout.getChoiceA();
        RPSMenu.RPS choice2 = timeout.getChoiceB();

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
    }

    private void onStrikeFirstChoiceTimeout(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long badUser = timeout.getUserMakingChoice();

        channel.editMessageById(messageId, String.format("%s, you didn't choose if you want to strike first in time.", MiscUtil.mentionUser(badUser)))
                .mentionUsers(badUser)
                .setActionRows()
                .queue();
    }

    private void onStrikeTimeout(@Nonnull StrikeStagesMenu.StrikeStagesTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long badUser = timeout.getCurrentStriker();

        channel.editMessageById(messageId, String.format("%s, you didn't strike the stage in time.", MiscUtil.mentionUser(badUser)))
                .mentionUsers(badUser)
                .setActionRows()
                .queue();
    }

    private void onStrikeResult(@Nonnull StrikeStagesMenu.StrikeResult result, @Nonnull ButtonClickEvent event) {
        Stage resultingStage = result.getRemainingStage();
        // In that case we have already printed the message
        if (resultingStage == null) return;

        event.editMessage(String.format("You have struck to %s.", resultingStage.getDisplayName())).setActionRows().queue();
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
