package com.github.gpluscb.toni.smashset;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class CharacterTree {
    @Nonnull
    private final CollectionType type;

    @Nonnull
    private final List<OneOfTwo<Character, CharacterTree>> characters;

    private CharacterTree(@Nonnull CollectionType type, @Nonnull List<OneOfTwo<Character, CharacterTree>> characters) {
        this.type = type;
        this.characters = characters;
    }

    /**
     * @throws IllegalArgumentException if the json is not as expected.
     */
    @Nonnull
    public static CharacterTree fromJson(@Nonnull JsonArray json) {
        return fromJson(CollectionType.ROOT, json);
    }

    @Nonnull
    private static CharacterTree fromJson(@Nonnull CollectionType collectionType, @Nonnull JsonArray json) {
        List<OneOfTwo<Character, CharacterTree>> characters = new ArrayList<>();

        try {
            for (JsonElement element : json) {
                JsonObject characterObject = element.getAsJsonObject();

                String type = characterObject.getAsJsonPrimitive("type").getAsString();

                switch (type) {
                    case "character" -> {
                        Character character = Character.fromJson(characterObject);
                        characters.add(OneOfTwo.ofT(character));
                    }
                    case "miis" -> {
                        CharacterTree miisTree = CharacterTree.fromJson(CollectionType.MIIS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(miisTree));
                    }
                    case "echos" -> {
                        CharacterTree echosTree = CharacterTree.fromJson(CollectionType.ECHOS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(echosTree));
                    }
                    case "sheik/zelda" -> {
                        CharacterTree sheikZeldaTree = CharacterTree.fromJson(CollectionType.SHEIK_ZELDA, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(sheikZeldaTree));
                    }
                    case "zss/samus" -> {
                        CharacterTree zssSamusTree = CharacterTree.fromJson(CollectionType.ZSS_SAMUS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(zssSamusTree));
                    }
                    default -> throw new IllegalArgumentException("Unsupported type: " + type);
                }
            }

            return new CharacterTree(collectionType, characters);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Exception while applying json", e);
        }
    }

    @Nonnull
    public List<Character> getAllCharacters() {
        List<Character> allCharacters = new ArrayList<>();

        for (OneOfTwo<Character, CharacterTree> element : characters)
            element.onT(allCharacters::add).onU(tree -> allCharacters.addAll(tree.getAllCharacters()));

        return allCharacters;
    }

    @Nonnull
    public List<Character> getAllCharacters(@Nullable Game game) {
        List<Character> allCharacters = new ArrayList<>();

        for (OneOfTwo<Character, CharacterTree> element : characters) {
            element.onT(character -> {
                if (game == null || character.games().contains(game)) allCharacters.add(character);
            }).onU(tree -> allCharacters.addAll(tree.getAllCharacters(game)));
        }

        return allCharacters;
    }

    @Nonnull
    public List<List<Character>> getAllCharacters(@Nullable Game game, boolean stackEchos, boolean stackMiis, boolean stackSheikZelda, boolean stackZssSamus) {
        List<List<Character>> allCharacters = new ArrayList<>();

        for (OneOfTwo<Character, CharacterTree> element : characters) {
            element.onT(character -> {
                if (game == null || character.games().contains(game))
                    allCharacters.add(Collections.singletonList(character));
            }).onU(tree -> {
                if ((stackEchos && tree.getType() == CollectionType.ECHOS)
                        || (stackMiis && tree.getType() == CollectionType.MIIS)
                        || (stackSheikZelda && tree.getType() == CollectionType.SHEIK_ZELDA)
                        || (stackZssSamus && tree.getType() == CollectionType.ZSS_SAMUS)) {
                    // Stacking
                    List<Character> stacked = tree.getAllCharacters(game);
                    if (!stacked.isEmpty()) allCharacters.add(stacked);
                } else {
                    // Recursively add
                    List<List<Character>> recursiveChars = tree.getAllCharacters(game, stackEchos, stackMiis, stackSheikZelda, stackZssSamus);
                    allCharacters.addAll(recursiveChars);
                }
            });
        }

        return allCharacters;
    }

    @Nonnull
    public CollectionType getType() {
        return type;
    }

    @Nonnull
    public List<OneOfTwo<Character, CharacterTree>> getCharacters() {
        return characters;
    }

    private enum CollectionType {
        ROOT,
        MIIS,
        ECHOS,
        SHEIK_ZELDA,
        ZSS_SAMUS,
    }

    public enum Game {
        SMASH_64("64"),
        MELEE("melee"),
        BRAWL("brawl", "pm", "project m"),
        SMASH_4("4", "u", "wii u", "3ds"),
        ULTIMATE("5", "ult", "ultimate", "switch");

        @Nonnull
        private final String[] gameNames;

        Game(@Nonnull String... gameNames) {
            this.gameNames = gameNames;
        }

        @Nullable
        public static Game getForName(@Nonnull String name) {
            for (Game game : Game.values())
                if (Arrays.stream(game.gameNames).anyMatch(name::equalsIgnoreCase)) return game;

            return null;
        }
    }
}
