package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.command.menu.SmashSetMenu;
import com.github.gpluscb.toni.db.DBManager;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.SmashSet;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmashSetCommand implements Command {
    private final static Logger log = LogManager.getLogger(SmashSetCommand.class);

    @Nonnull
    private final EventWaiter waiter;
    @Nonnull
    private final DBManager manager;
    @Nonnull
    private final List<Ruleset> rulesets;
    @Nonnull
    private final List<Character> characters;

    public SmashSetCommand(@Nonnull EventWaiter waiter, @Nonnull DBManager manager, @Nonnull List<Ruleset> rulesets, @Nonnull CharacterTree characterTree) {
        this.waiter = waiter;
        this.manager = manager;
        this.rulesets = rulesets;
        this.characters = characterTree.getAllCharacters();
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

        User user1;
        User user2;
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

        int bestOfWhat = 3;

        user1 = ctx.getOptionNonNull("player-1").getAsUser();

        OptionMapping user2Option = ctx.getOption("player-2");
        user2 = user2Option == null ? ctx.getUser() : user2Option.getAsUser();

        OptionMapping bestOfWhatOption = ctx.getOption("best-of");
        if (bestOfWhatOption != null) bestOfWhat = (int) bestOfWhatOption.getAsLong();

        OptionMapping rulesetIdMapping = ctx.getOption("ruleset-id");
        if (rulesetIdMapping != null) {
            long rulesetId = rulesetIdMapping.getAsLong();
            ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.rulesetId() == rulesetId).findAny().orElse(null);
            if (ruleset == null) {
                ctx.reply("The given ruleset id is invalid.").queue();
                return;
            }
        }

        if (user1.getIdLong() == user2.getIdLong()) {
            ctx.reply("You need two people for a game of smash!").queue();
            return;
        }

        if (user1.isBot() || user2.isBot()) {
            ctx.reply("Bot users are not supported!").queue();
            return;
        }

        if (bestOfWhat % 2 == 0) {
            ctx.reply("`BEST OF` argument can not be an even number - you can only play a `best of x` for odd values of `x`.").queue();
            return;
        }

        int firstToWhatScore = (bestOfWhat + 1) / 2;

        if (ruleset != null) {
            startSmashSet(ruleset, OneOfTwo.ofU(ctx.getEvent()), firstToWhatScore, user1, user2);
            return;
        }

        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                waiter,
                ctx.getMember(),
                ctx.getUser(),
                rulesets,
                (info, event) -> startSmashSet(info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()), firstToWhatScore, user1, user2)
        ));

        rulesetMenu.displaySlashReplying(ctx.getEvent());
    }

    private void startSmashSet(@Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandInteractionEvent> replyTo, int firstToWhatScore, @Nonnull User user1, @Nonnull User user2) {
        SmashSetMenu menu = new SmashSetMenu(new SmashSetMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(waiter)
                                .setTimeout(60, TimeUnit.MINUTES)
                                .build())
                        .setUsers(user1.getIdLong(), user2.getIdLong())
                        .build())
                .setBanTimeout(60, TimeUnit.MINUTES)
                .setDoubleBlindTimeout(60, TimeUnit.MINUTES)
                .setPickStageTimeout(60, TimeUnit.MINUTES)
                .setReportGameTimeout(60, TimeUnit.MINUTES)
                .setLoserCharCounterpickTimeout(60, TimeUnit.MINUTES)
                .setWinnerCharPickTimeout(60, TimeUnit.MINUTES)
                .setWaiter(waiter)
                .setCharacters(characters)
                .setRuleset(ruleset)
                .setFirstToWhatScore(firstToWhatScore)
                .setRpsInfo(new SmashSetMenu.RPSInfo(60, TimeUnit.MINUTES,
                        60, TimeUnit.MINUTES,
                        firstChoiceTimeout -> genericOnTimeout(firstChoiceTimeout.getTimeoutEvent(), "The choice for who will strike first timed out."),
                        rpsTimeout -> genericOnTimeout(rpsTimeout.getTimeoutEvent(), "The RPS menu timed out.")))
                .setUsersDisplay(user1.getName(), user2.getName())
                .setOnResult(this::onResult)
                .setOnStrikeTimeout(strikeTimeout -> genericOnTimeout(strikeTimeout.getTimeoutEvent(), "The stage strike menu timed out."))
                .setOnBanTimeout(banTimeout -> genericOnTimeout(banTimeout.getTimeoutEvent(), "The stage ban menu timed out."))
                .setOnDoubleBlindTimeout(doubleBlindTimeout -> genericOnTimeout(doubleBlindTimeout.getTimeoutEvent(), "The double blind menu timed out."))
                .setOnWinnerCharPickTimeout(winnerCharPickTimeout -> genericOnTimeout(winnerCharPickTimeout.getTimeoutEvent(), "The winner char pick timed out."))
                .setOnLoserCharCounterpickTimeout(loserCharPickTimeout -> genericOnTimeout(loserCharPickTimeout.getTimeoutEvent(), "The loser char counterpick timed out."))
                .setOnMessageChannelNotInCache(messageChannelNotInCache -> log.warn("Message channel not in cache."))
                .setOnReportGameTimeout(reportGameTimeout -> genericOnTimeout(reportGameTimeout.getTimeoutEvent(), "The game report menu timed out."))
                .setOnPickStageTimeout(pickStageTimeout -> genericOnTimeout(pickStageTimeout.getTimeoutEvent(), "The stage counterpick menu timed out."))
                .build());

        replyTo
                .onT(menu::displayReplying)
                .onU(menu::displaySlashReplying);
    }

    private void onResult(@Nonnull SmashSetMenu.SmashSetResult result, @Nonnull ButtonInteractionEvent event) {
        List<SmashSet.GameData> games = result.getSet().getGames();
        SmashSet.GameData lastGame = games.get(games.size() - 1);

        long winner;
        if (lastGame.getWinner() == SmashSet.Player.PLAYER1) {
            winner = result.getTwoUsersChoicesActionMenuSettings().user1();
        } else {
            winner = result.getTwoUsersChoicesActionMenuSettings().user2();
        }

        long user1Score = games.stream().filter(game -> game.getWinner() == SmashSet.Player.PLAYER1).count();
        long user2Score = games.stream().filter(game -> game.getWinner() == SmashSet.Player.PLAYER2).count();
        event.getHook()
                .sendMessage(String.format("%s, you won the set %d - %d. Congrats!", MiscUtil.mentionUser(winner), user1Score, user2Score))
                .mentionUsers(winner)
                .queue();
    }

    private void genericOnTimeout(@Nonnull ActionMenu.MenuStateInfo timeout, @Nonnull String message) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) {
            log.warn("Channel for timeout not in cache");
            return;
        }

        channel.editMessageById(timeout.getMessageId(), message).setComponents().queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS})
                .setShortHelp("[Beta] Helps you play a competitive set of Smash for a specific ruleset.")
                .setDetailedHelp("""
                        Guides you through a competitive set of Smash Bros. Ultimate according to a given ruleset. This will help you with double blind character picks, stage striking, game reporting, and character and stage counterpicking.
                        The `BEST OF X` argument specifies how many games you will play (i.e. with `3` you'll play a best of 3, with `5` you'll play a best of 5 etc.).
                        Slash command options:
                        • `player-1`: The first player.
                        • (Optional) `player-2`: The second player. This is yourself by default.
                        • (Optional) `best-of`: Will this be a best of 3/best of 5/best of whatever set? Default is 3.
                        • (Optional) `ruleset-id`: The ruleset id. By default, I will ask you to select a ruleset after you execute the command. Use the `rulesets` command to check out available rulesets.""")
                .setCommandData(Commands.slash("playset", "Helps you play a set in a specific ruleset")
                        .addOption(OptionType.USER, "player-1", "The first player", true)
                        .addOption(OptionType.USER, "player-2", "The opponent. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "best-of", "Will this be a best of 3/best of 5/best of whatever set? This is 3 by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
