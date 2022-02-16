package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.command.menu.SmashSetMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.SmashSet;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmashSetCommand implements Command {
    @Nonnull
    private final ChannelChoiceWaiter channelWaiter;
    @Nonnull
    private final List<Ruleset> rulesets;
    @Nonnull
    private final List<Character> characters;

    public SmashSetCommand(@Nonnull ChannelChoiceWaiter channelWaiter, @Nonnull List<Ruleset> rulesets, @Nonnull CharacterTree characterTree) {
        this.channelWaiter = channelWaiter;
        this.rulesets = rulesets;
        this.characters = characterTree.getAllCharacters();
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        long user1 = 107565973652938752L;
        long user2 = 348127811275456512L;
        Ruleset ruleset = null; // TODO: Default ruleset
        int firstToWhatScore = (3 + 1) / 2;

        if (ruleset != null) {
            startSmashSet(ruleset, ctx.getContext().mapT(MessageCommandContext::getMessage).mapU(SlashCommandContext::getEvent), firstToWhatScore, user1, user2);
            return;
        }

        long selectingUser = ctx.getUser().getIdLong();
        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                channelWaiter.getEventWaiter(),
                selectingUser,
                rulesets,
                (info, event) -> startSmashSet(info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()), firstToWhatScore, user1, user2)
        ));

        ctx.getContext()
                .onT(msg -> rulesetMenu.displayReplying(msg.getMessage()))
                .onU(slash -> rulesetMenu.displaySlashReplying(slash.getEvent()));
    }

    private void startSmashSet(@Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandEvent> replyTo, int firstToWhatScore, long user1, long user2) {
        SmashSetMenu menu = new SmashSetMenu(new SmashSetMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(channelWaiter.getEventWaiter())
                                .build())
                        .setUsers(user1, user2)
                        .build())
                .setChannelWaiter(channelWaiter)
                .setCharacters(characters)
                .setRuleset(ruleset)
                .setFirstToWhatScore(firstToWhatScore)
                .setRpsInfo(new SmashSetMenu.RPSInfo(20, TimeUnit.MINUTES, 20, TimeUnit.MINUTES, a -> {
                }, a -> {
                }))
                .setUsersDisplay("Mrü", "MarRueTest")
                .setOnResult(this::onResult)
                .build());

        replyTo
                .onT(menu::displayReplying)
                .onU(menu::displaySlashReplying);
    }

    private void onResult(@Nonnull SmashSetMenu.SmashSetResult result, @Nonnull ButtonClickEvent event) {
        List<SmashSet.GameData> games = result.getSet().getGames();
        SmashSet.GameData lastGame = games.get(games.size() - 1);

        long winner;
        if (lastGame.getWinner() == SmashSet.Player.PLAYER1) {
            winner = result.getTwoUsersChoicesActionMenuSettings().user1();
        } else {
            winner = result.getTwoUsersChoicesActionMenuSettings().user2();
        }

        event.getHook().sendMessage(String.format("Wowee %s you won the set congrats!!!!!!!!", MiscUtil.mentionUser(winner))).mentionUsers(winner).queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS})
                .setAliases(new String[]{"set", "playset"})
                .setShortHelp("Helps you play a competitive set of Smash for a specific ruleset. Usage: `set [PLAYER 1] <PLAYER 2> [BEST OF X] [RULESET ID]`")
                .setDetailedHelp("""
                        `set [PLAYER 1 (default: you)] <PLAYER 2> [BEST OF X (default: 3)] [RULESET ID (default: server default ruleset)]`
                        Guides you through a competitive set of Smash Bros. Ultimate according to a given ruleset. This will help you with double blind character picks, stage striking, game reporting, and character and stage counterpicking.
                        The `BEST OF X` argument specifies how many games you will play (i.e. with `3` you'll play a best of 3, with `5` you'll play a best of 5 etc.).
                        For a list of rulesets and their IDs, use the `rulesets` command.
                        Aliases: `set`, `playset`""")
                .setCommandData(new CommandData("playset", "Helps you play a set in a specific ruleset")
                        .addOption(OptionType.USER, "player-1", "The first player", true)
                        .addOption(OptionType.USER, "player-2", "The opponent. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "best-of", "This will be a best of 3/5/whatever set. This is 3 by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
