package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.menu.BanPickStagesMenu;
import com.github.gpluscb.toni.command.menu.BanStagesMenu;
import com.github.gpluscb.toni.command.menu.PickStageMenu;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
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
        Ruleset ruleset = null;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            OneOfTwo<MiscUtil.OneOrTwoUserArgs, MiscUtil.TwoUserArgsErrorType> result = MiscUtil.getTwoUserArgs(msg, true);

            MiscUtil.TwoUserArgsErrorType error = result.getU().orElse(null);
            if (error != null) {
                String reply = switch (error) {
                    case WRONG_NUMBER_ARGS -> "You must mention two users, and give the ruleset id.";
                    case NOT_USER_MENTION_ARG -> "Arguments must be user mentions.";
                    case BOT_USER -> "This command doesn't support bot or webhook users.";
                    case USER_1_EQUALS_USER_2 -> "I can't have someone do a stage ban/counterpick procedure with themselves, what would that even look like?";
                };

                ctx.reply(reply).queue();
                return;
            }

            MiscUtil.OneOrTwoUserArgs users = result.getTOrThrow();
            banningUser = users.getUser1();
            pickingUser = users.getUser2();

            int continuedArgsIdx = users.twoArgumentsGiven() ? 2 : 1;
            int argNum = msg.getArgNum();

            if (argNum == continuedArgsIdx + 1) {
                String rulesetIdString = msg.getArg(continuedArgsIdx);
                try {
                    int rulesetId = Integer.parseInt(rulesetIdString);
                    ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.getRulesetId() == rulesetId).findAny().orElse(null);
                    if (ruleset == null) {
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

        if (ruleset != null) {
            startCounterpickStages(pickingUser, banningUser, ruleset, context.mapT(MessageCommandContext::getMessage).mapU(SlashCommandContext::getEvent));
            return;
        }

        long selectingUser = ctx.getUser().getIdLong();
        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                waiter,
                selectingUser,
                rulesets,
                (info, event) -> startCounterpickStages(pickingUser, banningUser, info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()))
        ));

        context
                .onT(msg -> rulesetMenu.displayReplying(msg.getMessage()))
                .onU(slash -> rulesetMenu.displaySlashReplying(slash.getEvent()));
    }

    private void startCounterpickStages(long pickingUser, long banningUser, @Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandEvent> replyTo) {
        Message pickStagesStart = new MessageBuilder(String.format("%s, since %s has chosen their bans, you can now pick one stage from the remaining stages.",
                MiscUtil.mentionUser(pickingUser),
                MiscUtil.mentionUser(banningUser)))
                .mentionUsers(banningUser, pickingUser)
                .build();

        BanPickStagesMenu menu = new BanPickStagesMenu(new BanPickStagesMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(waiter)
                                .build())
                        .setUsers(banningUser, pickingUser)
                        .build())
                .setRuleset(ruleset)
                .setPickStageStart(pickStagesStart)
                .setOnResult(this::onResult)
                .setOnBanTimeout(this::onBanTimeout)
                .setOnPickTimeout(this::onPickTimeout)
                .build());

        replyTo
                .onT(menu::displayReplying)
                .onU(menu::displaySlashReplying);
    }

    private void onBanTimeout(@Nonnull BanStagesMenu.BanStagesTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long banningUser = timeout.getBanStagesMenuSettings().banningUser();

        channel.editMessageById(messageId, String.format("%s, you didn't ban the stages in time.", MiscUtil.mentionUser(banningUser)))
                .mentionUsers(banningUser)
                .setActionRows()
                .queue();
    }

    private void onPickTimeout(@Nonnull PickStageMenu.PickStageTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long pickingUser = timeout.getPickStageMenuSettings().pickingUser();

        channel.editMessageById(messageId, String.format("%s, you didn't pick the stage.", MiscUtil.mentionUser(pickingUser)))
                .mentionUsers(pickingUser)
                .setActionRows()
                .queue();
    }

    private void onResult(@Nonnull BanPickStagesMenu.BanPickStagesResult result, @Nonnull ButtonClickEvent event) {
        Stage pickedStage = result.getPickResult().getPickedStage();

        event.editMessage(String.format("You will be playing your next game on %s!", pickedStage.getDisplayName()))
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
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
