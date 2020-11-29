package com.github.gpluscb.smashggnotifications.command.game;

import com.github.gpluscb.smashggnotifications.command.Command;
import com.github.gpluscb.smashggnotifications.command.CommandContext;
import com.github.gpluscb.smashggnotifications.util.CharacterTree;
import com.github.gpluscb.smashggnotifications.util.MiscUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class RandomCharacterCommand implements Command {
    @Nonnull
    private final CharacterTree characterTree;

    public RandomCharacterCommand(@Nonnull CharacterTree characterTree) {
        this.characterTree = characterTree;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        // TODO: Neat system for getting arguments out of CommandContext with defaults optionals and such and such

        CharacterTree.Game game = CharacterTree.Game.ULTIMATE;
        boolean stackEchos = false;
        boolean stackMiis = false;
        boolean stackSheikZelda = false;
        boolean stackZssSamus = false;

        List<String> args = ctx.getArgs();
        int argsSize = args.size();
        if (argsSize > 0) {
            String gameName = args.get(0);
            game = CharacterTree.Game.getForName(gameName);
            if (game == null) {
                ctx.reply("I don't know that game. Please use one of the following: `64`, `melee`, `brawl`, `4`, `ult`.").queue();
                return;
            }

            // Stack Sheik/Zelda by default for these games
            if (game == CharacterTree.Game.BRAWL || game == CharacterTree.Game.MELEE) stackSheikZelda = true;
            // Stack ZSS/Samus by default for Brawl
            if (game == CharacterTree.Game.BRAWL) stackZssSamus = true;
        }

        if (argsSize > 1) {
            String stackEchosString = args.get(1);
            Boolean stackEchosTemp = MiscUtil.boolFromString(stackEchosString);
            if (stackEchosTemp == null) {
                ctx.reply("I don't understand if you want echos to be treated as one character or not. Please type either `true` or `false` for that argument.").queue();
                return;
            }

            stackEchos = stackEchosTemp;
        }

        if (argsSize > 2) {
            String stackMiisString = args.get(2);
            Boolean stackMiisTemp = MiscUtil.boolFromString(stackMiisString);
            if (stackMiisTemp == null) {
                ctx.reply("I don't understand if you want miis to be treated as one character or not. Please type either `true` or `false` for that argument.").queue();
                return;
            }

            stackMiis = stackMiisTemp;
        }

        if (argsSize > 3) {
            String stackSheikZeldaString = args.get(3);
            Boolean stackSheikZeldaTemp = MiscUtil.boolFromString(stackSheikZeldaString);
            if (stackSheikZeldaTemp == null) {
                ctx.reply("I don't understand if you want Sheik and Zelda to be treated as one character or not. Please type either `true` or `false` for that argument.").queue();
                return;
            }

            stackSheikZelda = stackSheikZeldaTemp;
        }

        if (argsSize > 4) {
            String stackZssSamusString = args.get(4);
            Boolean stackZssSamusTemp = MiscUtil.boolFromString(stackZssSamusString);
            if (stackZssSamusTemp == null) {
                ctx.reply("I don't understand if you want Zero Suit Samus and Samus to be treated as one character or not. Please type either `true` or `false` for that argument.").queue();
                return;
            }

            stackZssSamus = stackZssSamusTemp;
        }

        List<List<CharacterTree.Character>> possibleCharacters = characterTree.getAllCharacters(game, stackEchos, stackMiis, stackSheikZelda, stackZssSamus);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(possibleCharacters.size());
        List<CharacterTree.Character> selectedCharacters = possibleCharacters.get(rngIndex);

        sendResponse(ctx, selectedCharacters);
    }

    private void sendResponse(@Nonnull CommandContext ctx, @Nonnull List<CharacterTree.Character> selectedCharacters) {
        String emotes = selectedCharacters.stream().map(character -> String.format("%s(%s)", MiscUtil.mentionEmote(character.getEmoteId()), character.getName())).collect(Collectors.joining("/"));

        ctx.reply(emotes).queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"random"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Picks a random character for you. Usage: `random [GAME] [TREAT ECHOS AS ONE?] [TREAT MIIS AS ONE?] [TREAT SHEIK/ZELDA AS ONE?] [TREAT SAMUS/ZSS AS ONE?]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`random [GAME (64|melee|brawl|4|ult(default))] [TREAT ECHOS AS ONE CHARACTER? (true|false(default))] [TREAT MIIS AS ONE CHARACTER? (true|false(default))] [TREAT SHEIK/ZELDA AS ONE CHARACTER? (true(default for Melee/Brawl)|false(default for 4/Ultimate))] [TREAT ZERO SUIT SAMUS/SAMUS AS ONE CHARACTER? (true(default for Brawl)|false(default for 4/Ultimate))]`\n" +
                "Selects a random character from the roster of a smash game.";
    }
}
