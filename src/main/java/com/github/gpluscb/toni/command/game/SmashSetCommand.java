package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.command.components.SmashSetMenu;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import com.github.gpluscb.toni.util.smash.Character;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.SmashSet;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.List;

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
        Ruleset ruleset = rulesets.get(0);
        int firstToWhatScore = (3 + 1) / 2;

        SmashSetMenu menu = new SmashSetMenu.Builder()
                .setChannelWaiter(channelWaiter)
                .setUsers(user1, user2)
                .setCharacters(characters)
                .setRuleset(ruleset)
                .setFirstToWhatScore(firstToWhatScore)
                .setUsersDisplay("Mrü", "MarRueTest")
                .setOnResult(this::onResult)
                .build();

        ctx.getContext()
                .onT(msg -> menu.displayReplying(msg.getMessage()))
                .onU(slash -> menu.displaySlashReplying(slash.getEvent()));
    }

    private void onResult(@Nonnull SmashSetMenu.SmashSetResult result, @Nonnull ButtonClickEvent event) {
        List<SmashSet.GameData> games = result.getSet().getGames();
        SmashSet.GameData lastGame = games.get(games.size() - 1);

        long winner;
        if (lastGame.getWinner() == SmashSet.Player.PLAYER1) {
            winner = result.getUser1();
        } else {
            winner = result.getUser2();
        }

        event.editMessage(new MessageBuilder(String.format("Wowee %s you won!!!!", MiscUtil.mentionUser(winner))).mentionUsers(winner).build()).setActionRows().queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"set", "playset"})
                // TODO: Help
                .setCommandData(new CommandData("playset", "Helps you play a set in a specific ruleset")
                        .addOption(OptionType.USER, "player-1", "The first player", true)
                        .addOption(OptionType.USER, "player-2", "The opponent. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id", false) // TODO: Yea that feels unintuitive
                        .addOption(OptionType.INTEGER, "best-of", "This will be a best of 3/5/whatever set. This is 3 by default", false))
                .build();
    }
}
