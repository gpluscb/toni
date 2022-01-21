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
import com.github.gpluscb.toni.util.discord.menu.ActionMenu;
import com.github.gpluscb.toni.util.discord.menu.TwoUsersChoicesActionMenu;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;

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

        // TODO: Hella duped code
        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(new RulesetSelectMenu.Settings.Builder()
                .setActionMenuSettings(new ActionMenu.Settings.Builder()
                        .setWaiter(channelWaiter.getEventWaiter())
                        .setTimeout(15, TimeUnit.MINUTES)
                        .build())
                .setUser(ctx.getUser().getIdLong())
                .setRulesets(rulesets)
                .setStart(new MessageBuilder(String.format("%s, please select a ruleset.", ctx.getUser().getAsMention()))
                        .mentionUsers(ctx.getUser().getIdLong())
                        .build())
                .setOnRulesetSelect((info, event) -> onRulesetSelect(info, event, firstToWhatScore, user1, user2))
                .setOnTimeout(this::onRulesetSelectTimeout)
                .build());

        ctx.getContext()
                .onT(msg -> rulesetMenu.displayReplying(msg.getMessage()))
                .onU(slash -> rulesetMenu.displaySlashReplying(slash.getEvent()));
    }

    private synchronized void onRulesetSelect(@Nonnull RulesetSelectMenu.RulesetSelectionInfo info, @Nonnull SelectionMenuEvent event, int firstToWhatScore, long user1, long user2) {
        event.deferEdit().queue();
        startSmashSet(info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()), firstToWhatScore, user1, user2);
    }

    private synchronized void onRulesetSelectTimeout(@Nonnull RulesetSelectMenu.RulesetSelectTimeoutEvent timeout) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) return;
        if (channel instanceof TextChannel textChannel) {
            if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_HISTORY))
                return;
        }

        channel.retrieveMessageById(timeout.getMessageId())
                .flatMap(m -> m.editMessage("You didn't choose the ruleset in time.").setActionRows())
                .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
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
                // TODO: Help
                .setCommandData(new CommandData("playset", "Helps you play a set in a specific ruleset")
                        .addOption(OptionType.USER, "player-1", "The first player", true)
                        .addOption(OptionType.USER, "player-2", "The opponent. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id", false) // TODO: Yea that feels unintuitive
                        .addOption(OptionType.INTEGER, "best-of", "This will be a best of 3/5/whatever set. This is 3 by default", false))
                .build();
    }
}
