package com.github.gpluscb.toni.util.smash;

import com.github.gpluscb.toni.util.MiscUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Character(@Nullable Short id, @Nonnull String name,
                        @Nonnull List<String> altNames, long emoteId, long guildId,
                        @Nonnull Set<CharacterTree.Game> games) {
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

    @Nonnull
    public String getDisplayName() {
        return String.format("%s(%s)", MiscUtil.mentionEmote(emoteId()), name());
    }
}
