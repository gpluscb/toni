package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.smash.CharacterTree;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

// TODO: Melee Wolf? P+ Mewtwo?? This gonna be a headache
public class RandomCharacterCommand implements Command {
    private static final Logger log = LogManager.getLogger(RandomCharacterCommand.class);

    @Nonnull
    private final CharacterTree characterTree;

    public RandomCharacterCommand(@Nonnull CharacterTree characterTree) {
        this.characterTree = characterTree;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        CharacterTree.Game game = CharacterTree.Game.ULTIMATE;
        boolean stackEchos = false;
        boolean stackMiis = false;
        boolean stackSheikZelda = false;
        boolean stackZssSamus = false;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            List<String> args = msg.getArgs();
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
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            String gameName = slash.getEvent().getSubcommandName();

            if (gameName != null)
                game = CharacterTree.Game.getForName(gameName);

            if (game == null) {
                log.error("Game name from subcommand not found: {}", gameName);
                ctx.reply("This is a bad error - the subcommand you used apparently does not correspond to any game. I've notified my dev, but you can give them some context too.").queue();
                return;
            }

            // Stack Sheik/Zelda by default for these games
            if (game == CharacterTree.Game.BRAWL || game == CharacterTree.Game.MELEE) stackSheikZelda = true;
            // Stack ZSS/Samus by default for Brawl
            if (game == CharacterTree.Game.BRAWL) stackZssSamus = true;

            OptionMapping stackSheikZeldaMapping = slash.getOption("stack-sheik-zelda");
            if (stackSheikZeldaMapping != null) stackSheikZelda = stackSheikZeldaMapping.getAsBoolean();

            OptionMapping stackZSSSamusMapping = slash.getOption("stack-samus-zss");
            if (stackZSSSamusMapping != null) stackZssSamus = stackZSSSamusMapping.getAsBoolean();

            OptionMapping stackMiisMapping = slash.getOption("stack-miis");
            if (stackMiisMapping != null) stackMiis = stackMiisMapping.getAsBoolean();

            OptionMapping stackEchosMapping = slash.getOption("stack-echos");
            if (stackEchosMapping != null) stackEchos = stackEchosMapping.getAsBoolean();
        }

        List<List<CharacterTree.Character>> possibleCharacters = characterTree.getAllCharacters(game, stackEchos, stackMiis, stackSheikZelda, stackZssSamus);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(possibleCharacters.size());
        List<CharacterTree.Character> selectedCharacters = possibleCharacters.get(rngIndex);

        sendResponse(ctx, selectedCharacters);
    }

    private void sendResponse(@Nonnull CommandContext<?> ctx, @Nonnull List<CharacterTree.Character> selectedCharacters) {
        String emotes = selectedCharacters.stream()
                .map(character -> String.format("%s(%s)", MiscUtil.mentionEmote(character.getEmoteId()), character.getName()))
                .collect(Collectors.joining("/"));

        ctx.reply(emotes).queue();
    }

    private void tooManyArgs(@Nonnull CommandContext<?> ctx) {
        ctx.reply("Too many arguments. Use `/help random` for a detailed description.").queue();
    }

    @Nullable
    private Boolean shouldStackX(@Nonnull CommandContext<?> ctx, @Nonnull String arg, @Nonnull String name) {
        Boolean stackX = MiscUtil.boolFromString(arg);
        if (stackX == null)
            ctx.reply(String.format("I don't understand if you want %s to be treated as one character or not. " +
                    "Please type either `true` or `false` for that argument.", name)).queue();
        return stackX;
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"random", "randomchar", "random-char", "random-character", "randomcharacter"})
                .setShortHelp("Picks a random character for you. Usage: `random [GAME] [GAME SPECIFIC OPTIONS...]`")
                .setDetailedHelp("`random` (default game is ult)\n" +
                        "`random 64`\n" +
                        "`random melee [TREAT SHEIK/ZELDA AS ONE CHARACTER? (true(default)|false)]`\n" +
                        "`random brawl [TREAT SHEIK/ZELDA AS ONE? (true(default)|false)] [TREAT SAMUS/ZSS AS ONE? (true(default)|false)]`\n" +
                        "`random 4 [TREAT MIIS AS ONE? (true|false(default))]`\n" +
                        "`random ult [TREAT MIIS AS ONE? (true|false(default))] [TREAT ECHOES AS ONE? (true|false(default))]`\n" +
                        "Selects a random character from the roster of a smash game.\n" +
                        "Aliases: `random`, `randomchar`, `randomcharacter`")
                .setCommandData(new CommandData("random", "Picks a random character")
                        .addSubcommands(new SubcommandData("64", "Random character for smash 64"),
                                new SubcommandData("melee", "Random character for melee")
                                        .addOption(OptionType.BOOLEAN, "stack-sheik-zelda", "Should I treat Sheik and Zelda as a single character? Default is true.", false),
                                new SubcommandData("brawl", "Random character for brawl")
                                        .addOption(OptionType.BOOLEAN, "stack-sheik-zelda", "Should I treat Sheik and Zelda as a single character? Default is true.", false)
                                        .addOption(OptionType.BOOLEAN, "stack-samus-zss", "Should I treat Samus and ZSS as a single character? Default is true.", false),
                                new SubcommandData("4", "Random character for smash 4")
                                        .addOption(OptionType.BOOLEAN, "stack-miis", "Should I treat the Miis as a single character? Default is false.", false),
                                new SubcommandData("ult", "Random character for smash ultimate")
                                        .addOption(OptionType.BOOLEAN, "stack-miis", "Should I treat the Miis as a single character? Default is false.", false)
                                        .addOption(OptionType.BOOLEAN, "stack-echos", "Should I treat echo fighters like they are the same as their base character? Default is false.", false))
                ).build();
    }
}
