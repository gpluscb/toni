package com.github.gpluscb.toni.smashdata;

import com.google.gson.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmashdataManager {
    @Nonnull
    private Connection connection;
    @Nonnull
    private Map<String, Integer> pgru;
    @Nonnull
    private final Gson gson;

    public SmashdataManager(@Nonnull String dbLocation) throws SQLException {
        gson = new Gson();

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        pgru = loadPgru();
    }

    public synchronized void updateDb(@Nonnull String dbLocation) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        pgru = loadPgru();
    }

    private Map<String, Integer> loadPgru() throws SQLException {
        Statement pgruStatement = connection.createStatement();
        pgruStatement.setQueryTimeout(10);
        ResultSet rs = pgruStatement.executeQuery("SELECT by_id FROM ranking_seasons WHERE season = 2");

        rs.next();
        String pgruRaw = rs.getString("by_id");

        pgruStatement.close();

        JsonObject pgruJson = JsonParser.parseString(pgruRaw).getAsJsonObject();

        Map<String, Integer> pgru = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : pgruJson.entrySet())
            pgru.put(entry.getKey(), entry.getValue().getAsInt());

        return pgru;
    }

    public List<PlayerData> loadSmashdataByTag(@Nonnull String requestedTag) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT player_id, tag, prefixes, characters, country, state, region, social FROM players WHERE UPPER(tag) = ?");
        statement.setQueryTimeout(10);

        statement.setString(1, requestedTag.toUpperCase());

        List<PlayerData> results = new ArrayList<>();

        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            String id = rs.getString("player_id");
            String tag = rs.getString("tag");
            String prefixesRaw = rs.getString("prefixes");
            String charactersRaw = rs.getString("characters");
            String country = rs.getString("country");
            String state = rs.getString("state");
            String region = rs.getString("region");
            String socialRaw = rs.getString("social");

            List<String> prefixes = new ArrayList<>();

            JsonArray prefixesJson = prefixesRaw == null || prefixesRaw.isEmpty() || charactersRaw.equals("\"\"") ? null : JsonParser.parseString(prefixesRaw).getAsJsonArray();
            if (prefixesJson != null)
                for (JsonElement element : prefixesJson)
                    prefixes.add(element.getAsString());

            // WTF why is there just "\"\"" in the dataset for people with no character data instead of null or an empty string????? Not my dataset btw...
            JsonObject charactersJson = charactersRaw == null || charactersRaw.isEmpty() || charactersRaw.equals("\"\"") ? null : JsonParser.parseString(charactersRaw).getAsJsonObject();

            Map<String, Integer> characters = new HashMap<>();

            if (charactersJson != null)
                for (Map.Entry<String, JsonElement> entry : charactersJson.entrySet())
                    characters.put(entry.getKey(), entry.getValue().getAsInt());

            Social social = gson.fromJson(socialRaw, Social.class);

            Integer ranking = pgru.get(id);

            PlayerData data = new PlayerData(id, tag, prefixes, social, country, state, region, characters, ranking);
            results.add(data);
        }

        statement.close();

        return results;
    }

    public void shutdown() throws SQLException {
        connection.close();
    }

    public static class Social {
        @Nonnull
        private final List<String> twitter;

        public Social(@Nonnull List<String> twitter) {
            this.twitter = twitter;
        }

        @Nonnull
        public List<String> getTwitter() {
            return twitter;
        }
    }

    public static class PlayerData {
        @Nonnull
        private final String id;
        @Nonnull
        private final String tag;
        @Nonnull
        private final List<String> prefixes;
        @Nonnull
        private final Social social;
        @Nullable
        private final String country;
        @Nullable
        private final String state;
        @Nullable
        private final String region;
        @Nonnull
        private final Map<String, Integer> characters;
        @Nullable
        private final Integer pgru;

        public PlayerData(@Nonnull String id, @Nonnull String tag, @Nonnull List<String> prefixes, @Nonnull Social social, @Nullable String country, @Nullable String state, @Nullable String region, @Nonnull Map<String, Integer> characters, @Nullable Integer pgru) {
            this.id = id;
            this.tag = tag;
            this.prefixes = prefixes;
            this.social = social;
            this.country = country;
            this.state = state;
            this.region = region;
            this.characters = characters;
            this.pgru = pgru;
        }

        @Nonnull
        public String getId() {
            return id;
        }

        @Nonnull
        public String getTag() {
            return tag;
        }

        @Nonnull
        public List<String> getPrefixes() {
            return prefixes;
        }

        @Nonnull
        public Social getSocial() {
            return social;
        }

        @Nullable
        public String getCountry() {
            return country;
        }

        @Nullable
        public String getState() {
            return state;
        }

        @Nullable
        public String getRegion() {
            return region;
        }

        @Nonnull
        public Map<String, Integer> getCharacters() {
            return characters;
        }

        @Nullable
        public Integer getPgru() {
            return pgru;
        }
    }
}
