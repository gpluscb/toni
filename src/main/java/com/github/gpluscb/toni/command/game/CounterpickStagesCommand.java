package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.components.BanPickStagesMenu;
import com.github.gpluscb.toni.command.components.BanStagesMenu;
import com.github.gpluscb.toni.command.components.PickStageMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.List;

public class CounterpickStagesCommand implements Command {
    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final List<Ruleset> rulesets;

    public CounterpickStagesCommand(@Nonnull EventWaiter waiter, @Nonnull List<Ruleset> rulesets) {
        this.waiter = waiter;
        this.rulesets = rulesets;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        long banningUser;
        long pickingUser;
        // TODO: Server default ruleset
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
                        reply = "You must mention two users, and give the ruleset id.";
                        break;
                    case NOT_USER_MENTION_ARG:
                        reply = "Arguments must be user mentions.";
                        break;
                    case BOT_USER:
                        reply = "This command doesn't support bot or webhook users.";
                        break;
                    case USER_1_EQUALS_USER_2:
                        reply = "I can't have someone do a stage ban/counterpick procedure with themselves, what would that even look like?";
                        break;
                    default:
                        throw new IllegalStateException("Non exhaustive switch over error");
                }

                ctx.reply(reply).queue();
                return;
            }

            MiscUtil.OneOrTwoUserArgs users = result.getTOrThrow();
            banningUser = users.getUser1();
            pickingUser = users.getUser2();

            int continuedArgsIdx = users.isTwoArgumentsGiven() ? 2 : 1;
            int argNum = msg.getArgNum();

            if (argNum == continuedArgsIdx + 1) {
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
            } else if (argNum > continuedArgsIdx + 2) {
                ctx.reply("You gave too many arguments. Use `toni, help strike` for details.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            banningUser = slash.getOptionNonNull("banning-user").getAsUser().getIdLong();

            OptionMapping pickingUserMapping = slash.getOption("counterpicking-user");
            pickingUser = (pickingUserMapping == null ? ctx.getUser() : pickingUserMapping.getAsUser())
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
        }

        BanPickStagesMenu menu = new BanPickStagesMenu.Builder()
                .setWaiter(waiter)
                .setUsers(banningUser, pickingUser)
                .setRuleset(ruleset)
                .setOnResult(this::onResult)
                .setOnBanTimeout(this::onBanTimeout)
                .setOnPickTimeout(this::onPickTimeout)
                .build();

        context
                .onT(msg -> menu.display(msg.getMessage()))
                .onU(slash -> menu.displaySlashReplying(slash.getEvent()));
    }

    private void onBanTimeout(@Nonnull BanStagesMenu.BanStagesTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long banningUser = timeout.getBanningUser();

        channel.editMessageById(messageId, String.format("%s, you didn't ban the stages in time.", MiscUtil.mentionUser(banningUser)))
                .mentionUsers(banningUser)
                .setActionRows()
                .queue();
    }

    private void onPickTimeout(@Nonnull PickStageMenu.PickStageTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long pickingUser = timeout.getPickingUser();

        channel.editMessageById(messageId, String.format("%s, you didn't pick the stage.", MiscUtil.mentionUser(pickingUser)))
                .mentionUsers(pickingUser)
                .setActionRows()
                .queue();
    }

    private void onResult(@Nonnull BanPickStagesMenu.BanPickStagesResult result, @Nonnull ButtonClickEvent event) {
        Stage pickedStage = result.getPickResult().getPickedStage();

        event.editMessage(String.format("You will be playing your next game on %s!", pickedStage.getName()))
                .setActionRows()
                .queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"counterpick", "banstages"})
                // TODO: Help
                .setCommandData(new CommandData("counterpick", "Helps you through the stage ban/counterpick phase of a set")
                        .addOption(OptionType.USER, "banning-user", "The user banning the stage", true)
                        .addOption(OptionType.USER, "counterpicking-user", "The user counterpicking. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
