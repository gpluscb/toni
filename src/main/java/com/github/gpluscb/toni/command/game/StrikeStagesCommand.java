package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.command.menu.RPSAndStrikeStagesMenu;
import com.github.gpluscb.toni.command.menu.RPSMenu;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.command.menu.StrikeStagesMenu;
import com.github.gpluscb.toni.db.DBManager;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.Stage;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.Permission;
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
import java.util.concurrent.ThreadLocalRandom;

public class StrikeStagesCommand implements Command {
    private static final Logger log = LogManager.getLogger(StrikeStagesCommand.class);

    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final DBManager manager;
    @Nonnull
    private final List<Ruleset> rulesets;

    public StrikeStagesCommand(@Nonnull EventWaiter waiter, @Nonnull DBManager manager, @Nonnull List<Ruleset> rulesets) {
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

        // TODO: Server default doRPS setting?
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

        long user1 = ctx.getOptionNonNull("striker-1").getAsUser().getIdLong();

        OptionMapping user2Mapping = ctx.getOption("striker-2");
        long user2 = (user2Mapping == null ? ctx.getUser() : user2Mapping.getAsUser())
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

        OptionMapping doRpsMapping = ctx.getOption("rps");
        boolean doRPS = doRpsMapping != null && doRpsMapping.getAsBoolean();

        if (ruleset != null) {
            startStrikeStages(ruleset, OneOfTwo.ofU(ctx.getEvent()), doRPS, user1, user2);
            return;
        }

        // Load RulesetSelectMenu
        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                waiter,
                ctx.getMember(),
                ctx.getUser(),
                rulesets,
                (info, event) -> startStrikeStages(info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()), doRPS, user1, user2)
        ));

        rulesetMenu.displaySlashReplying(ctx.getEvent());
    }

    private void startStrikeStages(@Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandInteractionEvent> replyTo, boolean doRPS, long user1, long user2) {
        int[] starterStrikePattern = ruleset.starterStrikePattern();
        if (starterStrikePattern.length == 0) {
            // Has exactly one element in this case
            Stage stage = ruleset.starters().get(0);
            String reply = String.format("This ruleset only has one starter weirdly. You're going to ~~Brazil~~ %s.", stage.getDisplayName());
            replyTo.map(msg -> msg.reply(reply), slash -> slash.reply(reply)).queue();
        }

        TwoUsersChoicesActionMenu.Settings.Builder twoUsersChoicesActionMenuSettingsBuilder = new TwoUsersChoicesActionMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(waiter)
                        .build());

        ActionMenu menu;
        if (doRPS) {
            MessageCreateData start = new MessageCreateBuilder()
                    .setContent(String.format(
                            "%s and %s, to figure out who strikes first, you will first play RPS.",
                            MiscUtil.mentionUser(user1),
                            MiscUtil.mentionUser(user2)
                    ))
                    .mentionUsers(user1, user2).build();

            menu = new RPSAndStrikeStagesMenu(new RPSAndStrikeStagesMenu.Settings.Builder()
                    .setTwoUsersChoicesActionMenuSettings(twoUsersChoicesActionMenuSettingsBuilder
                            .setUsers(user1, user2)
                            .build())
                    .setRuleset(ruleset)
                    .setStart(start)
                    .setOnStrikeResult(this::onStrikeResult)
                    .setOnRPSTimeout(this::onRPSTimeout)
                    .setOnStrikeFirstTimeout(this::onStrikeFirstChoiceTimeout)
                    .setOnStrikeTimeout(this::onStrikeTimeout)
                    .build());
        } else {
            if (ThreadLocalRandom.current().nextBoolean()) {
                // Randomly swap
                long tmp = user1;
                user1 = user2;
                user2 = tmp;
            }

            menu = new StrikeStagesMenu(new StrikeStagesMenu.Settings.Builder()
                    .setTwoUsersChoicesActionMenuSettings(twoUsersChoicesActionMenuSettingsBuilder
                            .setUsers(user1, user2)
                            .build())
                    .setRuleset(ruleset)
                    .setOnResult(this::onStrikeResult)
                    .setOnTimeout(this::onStrikeTimeout)
                    .build());
        }

        replyTo
                .onT(menu::displayReplying)
                .onU(menu::displaySlashReplying);
    }

    private void onRPSTimeout(@Nonnull RPSMenu.RPSTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long user1 = timeout.getTwoUsersChoicesActionMenuSettings().user1();
        long user2 = timeout.getTwoUsersChoicesActionMenuSettings().user2();

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
                .setComponents()
                .queue();
    }

    private void onStrikeFirstChoiceTimeout(@Nonnull RPSAndStrikeStagesMenu.StrikeFirstChoiceTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long badUser = timeout.getUserMakingChoice();

        channel.editMessageById(messageId, String.format("%s, you didn't choose if you want to strike first in time.", MiscUtil.mentionUser(badUser)))
                .mentionUsers(badUser)
                .setComponents()
                .queue();
    }

    private void onStrikeTimeout(@Nonnull StrikeStagesMenu.StrikeStagesTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        long messageId = timeout.getMessageId();

        long badUser = timeout.getCurrentStriker();

        channel.editMessageById(messageId, String.format("%s, you didn't strike the stage in time.", MiscUtil.mentionUser(badUser)))
                .mentionUsers(badUser)
                .setComponents()
                .queue();
    }

    private void onStrikeResult(@Nonnull StrikeStagesMenu.StrikeResult result, @Nonnull ButtonInteractionEvent event) {
        Stage resultingStage = result.getRemainingStage();
        // In that case we have already printed the message
        if (resultingStage == null) return;

        event.editMessage(String.format("You have struck to %s.", resultingStage.getDisplayName())).setComponents().queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY})
                .setShortHelp("[Beta] Helps you do the stage striking procedure with a specific ruleset.")
                // TODO If we get passed ctx here, we can actually name the server default ruleset
                .setDetailedHelp("""
                        Helps you perform the [stage striking procedure](https://www.ssbwiki.com/Stage_striking) for a given ruleset. Depending on the `DO RPS` argument, you'll play a game of RPS first to determine who gets to strike first.
                        Slash command options:
                        • `striker-1`: One participant.
                        • (Optional) `striker-2`: The other participant. This is yourself by default.
                        • (Optional) `rps`: Whether to play RPs for who strikes first. Default is to randomly determine first striker.
                        • (Optional) `ruleset-id`: The ruleset id. By default, I will ask you to select a ruleset after you execute the command. Use the `rulesets` command to check out available rulesets.""")
                .setCommandData(Commands.slash("strikestarters", "Helps you perform the stage striking procedure")
                        .addOption(OptionType.USER, "striker-1", "One participant in the striking procedure", true)
                        .addOption(OptionType.USER, "striker-2", "The other participant in the striking procedure. This is yourself by default", false)
                        .addOption(OptionType.BOOLEAN, "rps", "Whether to play RPS for who strikes first. By default the first striker is determined randomly", false)
                        // TODO: Only allow valid ids
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: server default ruleset
                .build();
    }
}
