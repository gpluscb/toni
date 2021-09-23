package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.MessageCommandContext;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import com.github.gpluscb.toni.util.MiscUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

// TODO: Melee Wolf? P+ Mewtwo?? This gonna be a headache
public class RandomCharacterCommand implements Command {
    @Nonnull
    private final CharacterTree characterTree;

    public RandomCharacterCommand(@Nonnull CharacterTree characterTree) {
        this.characterTree = characterTree;
    }

    @Override
    public void execute(@Nonnull MessageCommandContext ctx) {
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

        switch (game) {
            case SMASH_64:
                if (argsSize > 1) {
                    tooManyArgs(ctx);
                    return;
                }
                break;
            case MELEE:
                if (argsSize > 2) {
                    tooManyArgs(ctx);
                    return;
                }

                if (argsSize > 1) {
                    Boolean stackSheikZeldaTemp = shouldStackX(ctx, args.get(1), "Sheik and Zelda");
                    if (stackSheikZeldaTemp == null) return;
                    stackSheikZelda = stackSheikZeldaTemp;
                }
                break;
            case BRAWL:
                if (argsSize > 3) {
                    tooManyArgs(ctx);
                    return;
                }

                if (argsSize > 1) {
                    Boolean stackSheikZeldaTemp = shouldStackX(ctx, args.get(1), "Sheik and Zelda");
                    if (stackSheikZeldaTemp == null) return;
                    stackSheikZelda = stackSheikZeldaTemp;
                }

                if (argsSize > 2) {
                    Boolean stackZssSamusTemp = shouldStackX(ctx, args.get(2), "ZSS and Samus");
                    if (stackZssSamusTemp == null) return;
                    stackZssSamus = stackZssSamusTemp;
                }
                break;
            case SMASH_4:
                if (argsSize > 2) {
                    tooManyArgs(ctx);
                    return;
                }

                if (argsSize > 1) {
                    Boolean stackMiisTemp = shouldStackX(ctx, args.get(1), "Miis");
                    if (stackMiisTemp == null) return;
                    stackMiis = stackMiisTemp;
                }
                break;
            case ULTIMATE:
                if (argsSize > 3) {
                    tooManyArgs(ctx);
                    return;
                }

                if (argsSize > 1) {
                    Boolean stackMiisTemp = shouldStackX(ctx, args.get(1), "Miis");
                    if (stackMiisTemp == null) return;
                    stackMiis = stackMiisTemp;
                }

                if (argsSize > 2) {
                    Boolean stackEchosTemp = shouldStackX(ctx, args.get(2), "Echos");
                    if (stackEchosTemp == null) return;
                    stackEchos = stackEchosTemp;
                }
                break;
        }

        List<List<CharacterTree.Character>> possibleCharacters = characterTree.getAllCharacters(game, stackEchos, stackMiis, stackSheikZelda, stackZssSamus);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(possibleCharacters.size());
        List<CharacterTree.Character> selectedCharacters = possibleCharacters.get(rngIndex);

        sendResponse(ctx, selectedCharacters);
    }

    private void sendResponse(@Nonnull MessageCommandContext ctx, @Nonnull List<CharacterTree.Character> selectedCharacters) {
        String emotes = selectedCharacters.stream()
                .map(character -> String.format("%s(%s)", MiscUtil.mentionEmote(character.getEmoteId()), character.getName()))
                .collect(Collectors.joining("/"));

        ctx.reply(emotes).queue();
    }

    private void tooManyArgs(@Nonnull MessageCommandContext ctx) {
        ctx.reply("Too many arguments. Use `toni, help random` for a detailed description.").queue();
    }

    @Nullable
    private Boolean shouldStackX(@Nonnull MessageCommandContext ctx, @Nonnull String arg, @Nonnull String name) {
        Boolean stackX = MiscUtil.boolFromString(arg);
        if (stackX == null)
            ctx.reply(String.format("I don't understand if you want %s to be treated as one character or not. " +
                    "Please type either `true` or `false` for that argument.", name)).queue();
        return stackX;
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"random", "randomchar", "random-char", "random-character", "randomcharacter"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Picks a random character for you. Usage: `random [GAME] [GAME SPECIFIC OPTIONS...]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`random` (default game is ult)\n" +
                "`random 64`\n" +
                "`random melee [TREAT SHEIK/ZELDA AS ONE CHARACTER? (true(default)|false)]`\n" +
                "`random brawl [TREAT SHEIK/ZELDA AS ONE? (true(default)|false)] [TREAT SAMUS/ZSS AS ONE? (true(default)|false)]`\n" +
                "`random 4 [TREAT MIIS AS ONE? (true|false(default))]`\n" +
                "`random ult [TREAT MIIS AS ONE? (true|false(default))] [TREAT ECHOES AS ONE? (true|false(default))]`\n" +
                "Selects a random character from the roster of a smash game.\n" +
                "Aliases: `random`, `randomchar`, `randomcharacter`";
    }
}
