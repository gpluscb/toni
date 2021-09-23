package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.DMChoiceWaiter;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BlindPickCommand implements Command {
    @Nonnull
    private final DMChoiceWaiter waiter;

    @Nonnull
    private final List<CharacterTree.Character> characters;

    @Nonnull
    private final List<Long> usersDoingBlindPick;

    public BlindPickCommand(@Nonnull DMChoiceWaiter waiter, @Nonnull CharacterTree characterTree) {
        this.waiter = waiter;
        this.characters = characterTree.getAllCharacters();
        usersDoingBlindPick = new ArrayList<>();
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        List<Long> users = new ArrayList<>();

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            for (int i = 0; i < msg.getArgNum(); i++) {
                User user = msg.getUserMentionArg(i);
                if (user == null) {
                    ctx.reply("Arguments must be user mentions of users in this server.").queue();
                    return;
                }

                if (user.isBot()) {
                    ctx.reply("Sorry, I can't support bots or webhook users.").queue();
                    return;
                }

                long userId = user.getIdLong();
                if (users.contains(userId)) {
                    ctx.reply("Users must be unique.").queue();
                    return;
                }

                users.add(userId);
            }

            if (users.size() < 2) {
                ctx.reply("You must mention at least two (2) users.").queue();
                return;
            }

            if (users.size() > 8) {
                ctx.reply("You must not mention more than eight (8) users.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            User user1User = slash.getOptionNonNull("user 1").getAsUser();
            long user1 = user1User.getIdLong();
            users.add(user1);

            User user2User;
            OptionMapping user2Option = slash.getOption("user 2");
            if (user2Option == null) user2User = ctx.getUser();
            else user2User = user2Option.getAsUser();
            long user2 = user2User.getIdLong();

            if (user1User.isBot() || user2User.isBot()) {
                ctx.reply("Sorry, I can't support bots or webhook users.").queue();
                return;
            }

            if (user1 == user2) {
                ctx.reply("The users must be different from each other.").queue();
                return;
            }

            users.add(user2);
        }

        synchronized (usersDoingBlindPick) {
            if (usersDoingBlindPick.stream().anyMatch(users::contains)) {
                ctx.reply("Some of you ppl are already doing another blind pick. I can't have people doing two blind picks at the same time!").queue();
                return;
            }

            usersDoingBlindPick.addAll(users);
        }

        // TODO: Avoid too many mentions somehow -> could potentially be a vector for ping spams
        String userMentions = users.stream().map(id -> String.format("<@%d>", id)).collect(Collectors.joining(", "));
        long[] userMentionsArray = users.stream().mapToLong(u -> u).toArray(); // TODO: Could be a bit more efficient, do the two streams in one pass...

        boolean worked = waiter.waitForDMChoice(users, true, e -> {
            Message message = e.getMessage();
            String choice = message.getContentRaw();

            CharacterTree.Character character = characters.stream().filter(c -> c.getAltNames().contains(choice.toLowerCase())).findAny().orElse(null);
            if (character == null) message.reply("I don't know that character.").queue();
            else message.reply("Accepted!").queue();

            return Optional.ofNullable(character);
        }, map -> {
            String choices = users.stream().map(u -> {
                CharacterTree.Character c = map.get(u);
                return String.format("%s: %s(%s)", MiscUtil.mentionUser(u), MiscUtil.mentionEmote(c.getEmoteId()), c.getName());
            }).collect(Collectors.joining("\n"));

            ctx.reply(String.format("The characters have been decided:%n%n%s", choices)).mentionUsers(userMentionsArray).queue();
        }, 3, TimeUnit.MINUTES, map -> {
            // TODO: Variable naming
            String lazyIdiots = users.stream().filter(Predicate.not(map::containsKey)).map(MiscUtil::mentionUser).collect(Collectors.joining(", "));

            ctx.reply(String.format("The three (3) minutes are done. Not all of you have given me your characters. Shame on you, %s!", lazyIdiots)).mentionUsers(userMentionsArray).queue();
        });

        if (worked) // TODO: Trusts that this message will go through. Not that big an issue, but still iffy.
            ctx.reply(String.format("Alright, %s, please send me a DM with your character choice now. You have three (3) minutes!", userMentions)).mentionUsers(userMentionsArray).queue();
        else
            ctx.reply("Some of you fools already have a DM thing going on with me. I can't have you do multiple of those at the same time. I'd lose my mind!").queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"doubleblind", "doubleblindpick", "blind", "blindpick"})
                .setShortHelp("Helps you do a (double) blind pick. Usage: `blind <USERS...>`")
                .setDetailedHelp("`doubleblind <USERS...>`\n" +
                        "Assists you in doing a [blind pick](https://gist.github.com/gpluscb/559f00e750854b46c0a71827e094ab3e). " +
                        "After performing the command, everyone who participates in the blind pick will have to DM me. " +
                        "So you might have to unblock me (but what kind of monster would have me blocked in the first place?).\n" +
                        "The slash command version supports at most two (2) participants.\n" +
                        "Aliases: `doubleblind`, `blindpick`, `blind`")
                .setCommandData(new CommandData("doubleblind", "Helps you do a double blind pick")
                        .addOption(OptionType.USER, "player 1", "The first participant in the double blind", true)
                        .addOption(OptionType.USER, "player 2", "The second participant in the double blind. This is yourself by default", false))
                .build();
    }
}
