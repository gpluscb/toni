package com.github.gpluscb.toni.command.game;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.smashset.Character;
import com.github.gpluscb.toni.smashset.CharacterTree;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

// TODO: Melee Wolf? P+ Mewtwo?? This gonna be a headache
@SuppressWarnings("ClassCanBeRecord")
public class RandomCharacterCommand implements Command {
    private static final Logger log = LogManager.getLogger(RandomCharacterCommand.class);

    @Nonnull
    private final CharacterTree characterTree;

    public RandomCharacterCommand(@Nonnull CharacterTree characterTree) {
        this.characterTree = characterTree;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        CharacterTree.Game game = CharacterTree.Game.ULTIMATE;
        boolean stackEchos = false;
        boolean stackMiis = false;
        boolean stackSheikZelda = false;
        boolean stackZssSamus = false;

        String gameName = ctx.getEvent().getSubcommandName();

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

        OptionMapping stackSheikZeldaMapping = ctx.getOption("stack-sheik-zelda");
        if (stackSheikZeldaMapping != null) stackSheikZelda = stackSheikZeldaMapping.getAsBoolean();

        OptionMapping stackZSSSamusMapping = ctx.getOption("stack-samus-zss");
        if (stackZSSSamusMapping != null) stackZssSamus = stackZSSSamusMapping.getAsBoolean();

        OptionMapping stackMiisMapping = ctx.getOption("stack-miis");
        if (stackMiisMapping != null) stackMiis = stackMiisMapping.getAsBoolean();

        OptionMapping stackEchosMapping = ctx.getOption("stack-echos");
        if (stackEchosMapping != null) stackEchos = stackEchosMapping.getAsBoolean();

        List<List<Character>> possibleCharacters = characterTree.getAllCharacters(game, stackEchos, stackMiis, stackSheikZelda, stackZssSamus);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int rngIndex = rng.nextInt(possibleCharacters.size());
        List<Character> selectedCharacters = possibleCharacters.get(rngIndex);

        sendResponse(ctx, selectedCharacters);
    }

    private void sendResponse(@Nonnull CommandContext ctx, @Nonnull List<Character> selectedCharacters) {
        String emotes = selectedCharacters.stream()
                .map(Character::getDisplayName)
                .collect(Collectors.joining("/"));

        ctx.reply(emotes).queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setShortHelp("Picks a random character for you.")
                .setDetailedHelp("""
                        Selects a random character from the roster of a smash game.
                        `/random 64`: Use the Smash 64 roster.
                        `/random melee`: Use the Melee roster.
                        Slash command options:
                        • (Optional) `stack-sheik-zelda`: Whether I should treat Sheik and Zelda as the same character. Default is `true`.
                        `/random brawl`: Use the Brawl roster.
                        Slash command options:
                        • (Optional) `stack-sheik-zelda`: Whether I should treat Sheik and Zelda as the same character. Default is `true`.
                        • (Optional) `stack-samus-zss`: Whether I should treat Samus and ZSS as a single character. Default is `true`.
                        `random 4`: Use the Smash 4 roster.
                        Slash command options:
                        • (Optional) `stack-miis`: Whether I should treat all the Miis as a single character. Default is `false`.
                        `random ult`: Use the Smash Ultimate roster.
                        Slash command options:
                        • (Optional) `stack-miis`: Whether I should treat all the Miis as a single character. Default is `false`.
                        • (Optional) `stack-echos`: Whether I should treat different echo fighters as a single character. Default is `false`.""")
                .setCommandData(Commands.slash("random", "Picks a random character")
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
