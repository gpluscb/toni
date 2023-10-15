package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.command.menu.BanPickStagesMenu;
import com.github.gpluscb.toni.command.menu.BanStagesMenu;
import com.github.gpluscb.toni.command.menu.PickStageMenu;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.db.DBManager;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;

public class CounterpickStagesCommand implements Command {
    private static final Logger log = LogManager.getLogger(CounterpickStagesCommand.class);

    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final DBManager manager;
    @Nonnull
    private final List<Ruleset> rulesets;

    public CounterpickStagesCommand(@Nonnull EventWaiter waiter, @Nonnull DBManager manager, @Nonnull List<Ruleset> rulesets) {
        this.waiter = waiter;
        this.manager = manager;
        this.rulesets = rulesets;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        long guildId;
        if (ctx.getEvent().isFromGuild()) {
            //noinspection DataFlowIssue
            guildId = ctx.getEvent().getGuild().getIdLong();
        } else {
            ctx.reply("This command does not work in DMs.").queue();
            return;
        }

        long banningUser;
        long pickingUser;
        Ruleset ruleset;
        try {
            Long rulesetId = manager.loadForcedRuleset(guildId);
            if (rulesetId == null) {
                ruleset = null;
            } else {
                ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.rulesetId() == rulesetId).findAny().orElse(null);
                if (ruleset == null) {
                    log.error("Guild {} has invalid forced ruleset {}", guildId, rulesetId);
                }
            }
        } catch (SQLException e) {
            log.catching(e);
            ruleset = null;
        }

        banningUser = ctx.getOptionNonNull("banning-user").getAsUser().getIdLong();

        OptionMapping pickingUserMapping = ctx.getOption("counterpicking-user");
        pickingUser = (pickingUserMapping == null ? ctx.getUser() : pickingUserMapping.getAsUser())
                .getIdLong();

        OptionMapping rulesetIdMapping = ctx.getOption("ruleset-id");
        if (rulesetIdMapping != null) {
            long rulesetId = rulesetIdMapping.getAsLong();
            ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.rulesetId() == rulesetId).findAny().orElse(null);
            if (ruleset == null) {
                ctx.reply("The given ruleset id is invalid.").queue();
                return;
            }
        }

        if (ruleset != null) {
            startCounterpickStages(pickingUser, banningUser, ruleset, OneOfTwo.ofU(ctx.getEvent()));
            return;
        }

        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                waiter,
                ctx.getMember(),
                ctx.getUser(),
                rulesets,
                (info, event) -> startCounterpickStages(pickingUser, banningUser, info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()))
        ));

        rulesetMenu.displaySlashReplying(ctx.getEvent());
    }

    private void startCounterpickStages(long pickingUser, long banningUser, @Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandInteractionEvent> replyTo) {
        MessageCreateData pickStagesStart = new MessageCreateBuilder()
                .setContent(String.format("%s, since %s has chosen their bans, you can now pick one stage from the remaining stages.",
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
                .setComponents()
                .queue();
    }

    private void onPickTimeout(@Nonnull PickStageMenu.PickStageTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long pickingUser = timeout.getPickStageMenuSettings().pickingUser();

        channel.editMessageById(messageId, String.format("%s, you didn't pick the stage.", MiscUtil.mentionUser(pickingUser)))
                .mentionUsers(pickingUser)
                .setComponents()
                .queue();
    }

    private void onResult(@Nonnull BanPickStagesMenu.BanPickStagesResult result, @Nonnull ButtonInteractionEvent event) {
        Stage pickedStage = result.getPickResult().getPickedStage();

        event.editMessage(String.format("You will be playing your next game on %s!", pickedStage.getDisplayName()))
                .setComponents()
                .queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setShortHelp("[Beta] Helps you through the stage ban/counterpick phase of a set.")
                .setDetailedHelp("""
                        Helps you perform the [stage counterpicking procedure](https://www.ssbwiki.com/Counterpick) after a match for a given ruleset.
                        For a list of rulesets and their IDs, use the `rulesets` command.
                        Slash command options:
                        • `banning-user`: The user banning stages
                        • (Optional) `counterpicking-user`: The user counterpicking. This is yourself by default.
                        • (Optional) `ruleset-id`: The ruleset id. Use the `rulesets` command to check out the different rulesets. By default, I will ask you to select a ruleset after you execute the command.
                        """)
                .setCommandData(Commands.slash("counterpick", "Helps you through the stage ban/counterpick phase of a set")
                        .addOption(OptionType.USER, "banning-user", "The user banning stages", true)
                        .addOption(OptionType.USER, "counterpicking-user", "The user counterpicking. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
