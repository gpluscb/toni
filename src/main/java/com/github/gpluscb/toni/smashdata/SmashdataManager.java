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

    public SmashdataManager(@Nonnull String dbLocation, @Nonnull Gson gson) throws SQLException {
        this.gson = gson;

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

    public record Social(@Nonnull List<String> twitter) {
    }

    public record PlayerData(@Nonnull String id, @Nonnull String tag,
                             @Nonnull List<String> prefixes,
                             @Nonnull Social social,
                             @Nullable String country, @Nullable String state,
                             @Nullable String region,
                             @Nonnull Map<String, Integer> characters,
                             @Nullable Integer pgru) {
    }
}
