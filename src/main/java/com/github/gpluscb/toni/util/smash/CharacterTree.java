package com.github.gpluscb.toni.util.smash;

import com.github.gpluscb.toni.util.OneOfTwo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CharacterTree {
    @Nonnull
    private final CharacterTree.CollectionType type;

    @Nonnull
    private final List<OneOfTwo<Character, CharacterTree>> characters;

    private CharacterTree(@Nonnull CharacterTree.CollectionType type, @Nonnull List<OneOfTwo<Character, CharacterTree>> characters) {
        this.type = type;
        this.characters = characters;
    }

    /**
     * @throws IllegalArgumentException if the json is not as expected.
     */
    @Nonnull
    public static CharacterTree fromJson(@Nonnull JsonArray json) {
        return fromJson(CharacterTree.CollectionType.ROOT, json);
    }

    @Nonnull
    private static CharacterTree fromJson(@Nonnull CharacterTree.CollectionType collectionType, @Nonnull JsonArray json) {
        List<OneOfTwo<Character, CharacterTree>> characters = new ArrayList<>();

        try {
            for (JsonElement element : json) {
                JsonObject characterObject = element.getAsJsonObject();

                String type = characterObject.getAsJsonPrimitive("type").getAsString();

                switch (type) {
                    case "character":
                        Character character = Character.fromJson(characterObject);
                        characters.add(OneOfTwo.ofT(character));
                        break;
                    case "miis":
                        CharacterTree miisTree = CharacterTree.fromJson(CharacterTree.CollectionType.MIIS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(miisTree));
                        break;
                    case "echos":
                        CharacterTree echosTree = CharacterTree.fromJson(CharacterTree.CollectionType.ECHOS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(echosTree));
                        break;
                    case "sheik/zelda":
                        CharacterTree sheikZeldaTree = CharacterTree.fromJson(CharacterTree.CollectionType.SHEIK_ZELDA, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(sheikZeldaTree));
                        break;
                    case "zss/samus":
                        CharacterTree zssSamusTree = CharacterTree.fromJson(CharacterTree.CollectionType.ZSS_SAMUS, characterObject.get("characters").getAsJsonArray());
                        characters.add(OneOfTwo.ofU(zssSamusTree));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported type: " + type);
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
                if (game == null || character.getGames().contains(game)) allCharacters.add(character);
            }).onU(tree -> allCharacters.addAll(tree.getAllCharacters(game)));
        }

        return allCharacters;
    }

    @Nonnull
    public List<List<Character>> getAllCharacters(@Nullable Game game, boolean stackEchos, boolean stackMiis, boolean stackSheikZelda, boolean stackZssSamus) {
        List<List<Character>> allCharacters = new ArrayList<>();

        for (OneOfTwo<Character, CharacterTree> element : characters) {
            element.onT(character -> {
                if (game == null || character.getGames().contains(game))
                    allCharacters.add(Collections.singletonList(character));
            }).onU(tree -> {
                if ((stackEchos && tree.getType() == CharacterTree.CollectionType.ECHOS)
                        || (stackMiis && tree.getType() == CharacterTree.CollectionType.MIIS)
                        || (stackSheikZelda && tree.getType() == CharacterTree.CollectionType.SHEIK_ZELDA)
                        || (stackZssSamus && tree.getType() == CharacterTree.CollectionType.ZSS_SAMUS)) {
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
    public CharacterTree.CollectionType getType() {
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

    public static class Character {
        @Nullable
        private final Short id;
        @Nonnull
        private final String name;
        @Nonnull
        private final List<String> altNames;
        private final long emoteId;
        private final long guildId;
        @Nonnull
        private final Set<Game> games;

        private Character(@Nullable Short id, @Nonnull String name, @Nonnull List<String> altNames, long emoteId, long guildId, @Nonnull Set<Game> games) {
            this.id = id;
            this.name = name;
            this.altNames = altNames;
            this.emoteId = emoteId;
            this.guildId = guildId;
            this.games = games;
        }

        /**
         * @throws IllegalArgumentException if the json is not as expected
         */
        @Nonnull
        public static Character fromJson(@Nonnull JsonObject json) {
            try {
                JsonElement idJson = json.get("id");
                Short id = idJson.isJsonNull() ? null : idJson.getAsShort();
                String name = json.getAsJsonPrimitive("name").getAsString();
                List<String> altNames = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("alt_names")) altNames.add(element.getAsString());
                long emoteId = json.getAsJsonPrimitive("emote_id").getAsLong();
                long guildId = json.getAsJsonPrimitive("guild").getAsLong();
                Set<Game> games = new HashSet<>();
                for (JsonElement element : json.getAsJsonArray("games")) {
                    String game = element.getAsString();
                    games.add(Game.getForName(game));
                }

                return new Character(id, name, altNames, emoteId, guildId, games);
            } catch (Exception e) {
                throw new IllegalArgumentException("Exception while applying json", e);
            }
        }

        /**
         * null for poketrainer
         */
        @Nullable
        public Short getId() {
            return id;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        public List<String> getAltNames() {
            return altNames;
        }

        public long getEmoteId() {
            return emoteId;
        }

        public long getGuildId() {
            return guildId;
        }

        @Nonnull
        public Set<Game> getGames() {
            return games;
        }
    }
}