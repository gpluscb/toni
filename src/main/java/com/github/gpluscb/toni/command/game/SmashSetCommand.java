package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.command.menu.RulesetSelectMenu;
import com.github.gpluscb.toni.command.menu.SmashSetMenu;
import com.github.gpluscb.toni.menu.ActionMenu;
import com.github.gpluscb.toni.menu.TwoUsersChoicesActionMenu;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.smashset.Ruleset;
import com.github.gpluscb.toni.smashset.SmashSet;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.ChannelChoiceWaiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmashSetCommand implements Command {
    private final static Logger log = LogManager.getLogger(SmashSetCommand.class);

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
        User user1;
        User user2;
        Ruleset ruleset = null; // TODO: Default ruleset
        int bestOfWhat = 3;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();
            OneOfTwo<CommandUtil.OneOrTwoUserArgs, CommandUtil.TwoUserArgsErrorType> argResult = CommandUtil.getTwoUserArgs(msg, true);
            CommandUtil.TwoUserArgsErrorType error = argResult.getU().orElse(null);
            if (error != null) {
                String reply = switch (error) {
                    case WRONG_NUMBER_ARGS -> "You must mention either one or two users.";
                    case NOT_USER_MENTION_ARG -> "The first two arguments must be user mentions.";
                    case BOT_USER -> "This command doesn't support bot or webhook users.";
                    case USER_1_EQUALS_USER_2 -> "I can't have someone play a smash set with themselves, what would that even look like?";
                };

                ctx.reply(reply).queue();
            }

            CommandUtil.OneOrTwoUserArgs users = argResult.getTOrThrow();
            user1 = users.user1User();
            user2 = users.user2User();

            int continuedArgsIdx = users.twoArgumentsGiven() ? 2 : 1;
            int argNum = msg.getArgNum();

            if (argNum >= continuedArgsIdx + 1) {
                String bestOfString = msg.getArg(continuedArgsIdx);

                try {
                    bestOfWhat = Integer.parseInt(bestOfString);
                } catch (NumberFormatException e) {
                    ctx.reply("The `BEST OF` argument was not a number. Use `toni, help set` for detailed help.").queue();
                    return;
                }
            }

            if (argNum == continuedArgsIdx + 2) {
                String rulesetIdString = msg.getArg(continuedArgsIdx + 1);
                try {
                    int rulesetId = Integer.parseInt(rulesetIdString);
                    ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.rulesetId() == rulesetId).findAny().orElse(null);
                    if (ruleset == null) {
                        ctx.reply("The given ruleset id is invalid.").queue();
                        return;
                    }
                } catch (NumberFormatException e) {
                    ctx.reply("The ruleset id must be an integer. Use `toni, help strike` for details.").queue();
                    return;
                }
            } else if (argNum > continuedArgsIdx + 3) {
                ctx.reply("You gave too many arguments. Use `toni, help strike` for details.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            user1 = slash.getOptionNonNull("player-1").getAsUser();

            OptionMapping user2Option = slash.getOption("player-2");
            user2 = user2Option == null ? ctx.getUser() : user2Option.getAsUser();

            OptionMapping bestOfWhatOption = slash.getOption("best-of");
            if (bestOfWhatOption != null) bestOfWhat = (int) bestOfWhatOption.getAsLong();

            OptionMapping rulesetIdMapping = slash.getOption("ruleset-id");
            if (rulesetIdMapping != null) {
                long rulesetId = rulesetIdMapping.getAsLong();
                ruleset = rulesets.stream().filter(ruleset_ -> ruleset_.rulesetId() == rulesetId).findAny().orElse(null);
                if (ruleset == null) {
                    ctx.reply("The given ruleset id is invalid.").queue();
                    return;
                }
            }
        }

        if (bestOfWhat % 2 == 0) {
            ctx.reply("`BEST OF` argument can not be an even number - you can only play a `best of x` for odd values of `x`.").queue();
            return;
        }

        int firstToWhatScore = (bestOfWhat + 1) / 2;

        if (ruleset != null) {
            startSmashSet(ruleset, ctx.getContext().mapT(MessageCommandContext::getMessage).mapU(SlashCommandContext::getEvent), firstToWhatScore, user1, user2);
            return;
        }

        RulesetSelectMenu rulesetMenu = new RulesetSelectMenu(RulesetSelectMenu.Settings.getDefaultSettings(
                channelWaiter.getEventWaiter(),
                ctx.getMember(),
                ctx.getUser(),
                rulesets,
                (info, event) -> startSmashSet(info.getSelectedRuleset(), OneOfTwo.ofT(event.getMessage()), firstToWhatScore, user1, user2)
        ));

        ctx.getContext()
                .onT(msg -> rulesetMenu.displayReplying(msg.getMessage()))
                .onU(slash -> rulesetMenu.displaySlashReplying(slash.getEvent()));
    }

    private void startSmashSet(@Nonnull Ruleset ruleset, @Nonnull OneOfTwo<Message, SlashCommandEvent> replyTo, int firstToWhatScore, @Nonnull User user1, @Nonnull User user2) {
        SmashSetMenu menu = new SmashSetMenu(new SmashSetMenu.Settings.Builder()
                .setTwoUsersChoicesActionMenuSettings(new TwoUsersChoicesActionMenu.Settings.Builder()
                        .setActionMenuSettings(new ActionMenu.Settings.Builder()
                                .setWaiter(channelWaiter.getEventWaiter())
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
                .setChannelWaiter(channelWaiter)
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

        if (menu.isInitFailure()) {
            String failureMessage = "Some of you already have a DM thing going on with me, so I can't initiate the double blind pick procedure.";
            replyTo.map(message -> message.reply(failureMessage), slash -> slash.reply(failureMessage)).queue();
            return;
        }

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

    private void genericOnTimeout(@Nonnull ActionMenu.MenuStateInfo timeout, @Nonnull String message) {
        MessageChannel channel = timeout.getChannel();
        if (channel == null) {
            log.warn("Channel for timeout not in cache");
            return;
        }

        channel.editMessageById(timeout.getMessageId(), message).setActionRows().queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS})
                .setAliases(new String[]{"playset", "set"})
                .setShortHelp("Helps you play a competitive set of Smash for a specific ruleset. Usage: `playset [PLAYER 1] <PLAYER 2> [BEST OF X] [RULESET ID]`")
                .setDetailedHelp("""
                        `playset [PLAYER 1 (default: you)] <PLAYER 2> [BEST OF X (default: 3)] [RULESET ID (default: selection menu)]`
                        Guides you through a competitive set of Smash Bros. Ultimate according to a given ruleset. This will help you with double blind character picks, stage striking, game reporting, and character and stage counterpicking.
                        The `BEST OF X` argument specifies how many games you will play (i.e. with `3` you'll play a best of 3, with `5` you'll play a best of 5 etc.).
                        For a list of rulesets and their IDs, use the `rulesets` command.
                        Aliases: `playset`, `set`""")
                .setCommandData(new CommandData("playset", "Helps you play a set in a specific ruleset")
                        .addOption(OptionType.USER, "player-1", "The first player", true)
                        .addOption(OptionType.USER, "player-2", "The opponent. This is yourself by default", false)
                        .addOption(OptionType.INTEGER, "best-of", "Will this be a best of 3/best of 5/best of whatever set? This is 3 by default", false)
                        .addOption(OptionType.INTEGER, "ruleset-id", "The ruleset id. Use the 'rulesets' command to check out the different rulesets", false)) // TODO: Yea that feels unintuitive
                .build();
    }
}
