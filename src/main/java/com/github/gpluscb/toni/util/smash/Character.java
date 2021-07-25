package com.github.gpluscb.toni.util.smash;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Character {
    @Nullable
    private final Short id;
    @Nonnull
    private final String name;
    @Nonnull
    private final List<String> altNames;
    private final long emoteId;
    private final long guildId;
    @Nonnull
    private final Set<CharacterTree.Game> games;

    private Character(@Nullable Short id, @Nonnull String name, @Nonnull List<String> altNames, long emoteId, long guildId, @Nonnull Set<CharacterTree.Game> games) {
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
            Set<CharacterTree.Game> games = new HashSet<>();
            for (JsonElement element : json.getAsJsonArray("games")) {
                String game = element.getAsString();
                games.add(CharacterTree.Game.getForName(game));
            }

            return new Character(id, name, altNames, emoteId, guildId, games);
        } catch (Exception e) {
            throw new IllegalArgumentException("Exception while applying json", e);
        }
    }

    /**
     * null for poketrainer and pymy
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
    public Set<CharacterTree.Game> getGames() {
        return games;
    }
}
