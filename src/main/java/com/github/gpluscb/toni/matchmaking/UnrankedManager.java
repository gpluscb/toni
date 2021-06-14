package com.github.gpluscb.toni.matchmaking;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UnrankedManager {
    @Nonnull
    private final Connection connection;
    @Nonnull
    private final Map<Long, MatchmakingConfig> matchmakingConfigCache;

    public UnrankedManager(@Nonnull String dbLocation) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        matchmakingConfigCache = Collections.synchronizedMap(new HashMap<>());
    }

    @Nullable
    public MatchmakingConfig loadMatchmakingConfig(long guildId) throws SQLException {
        MatchmakingConfig cached = matchmakingConfigCache.get(guildId);
        if (cached != null) return cached;

        PreparedStatement statement = connection.prepareStatement("SELECT lfg_role_id, channel_id FROM unranked_matchmaking_configs WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        statement.setLong(1, guildId);

        ResultSet rs = statement.executeQuery();

        if (rs.isClosed()) return null;

        long lfgRoleId = rs.getLong("lfg_role_id");
        Long channelId = rs.getLong("channel_id");
        if (rs.wasNull()) channelId = null;

        statement.close();

        return new MatchmakingConfig(lfgRoleId, channelId);
    }

    /**
     * @return true if changes were made (the row didn't already exist)
     */
    public boolean storeMatchmakingConfig(long guildId, @Nonnull MatchmakingConfig config) throws SQLException {
        PreparedStatement statement = connection
                .prepareStatement("INSERT INTO unranked_matchmaking_configs (guild_id, lfg_role_id, channel_id) VALUES (?, ?, ?) ON CONFLICT (guild_id) DO NOTHING");
        statement.setQueryTimeout(10);

        statement.setLong(1, guildId);
        statement.setLong(2, config.getLfgRoleId());
        Long channelId = config.getChannelId();
        if (channelId == null) statement.setNull(3, Types.BIGINT);
        else statement.setLong(3, channelId);

        boolean affected;
        synchronized (matchmakingConfigCache) {
            affected = statement.executeUpdate() >= 1;

            matchmakingConfigCache.put(guildId, config);
        }

        statement.close();

        return affected;
    }

    /**
     * @return true if a row was affected
     */
    public boolean updateMatchmakingConfig(long guildId, @Nonnull MatchmakingConfig config) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("UPDATE unranked_matchmaking_configs SET lfg_role_id = ?, channel_id = ? WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        statement.setLong(1, config.getLfgRoleId());
        Long channelId = config.getChannelId();
        if (channelId == null) statement.setNull(2, Types.BIGINT);
        else statement.setLong(2, channelId);
        statement.setLong(3, guildId);

        boolean affected;
        synchronized (matchmakingConfigCache) {
            affected = statement.executeUpdate() >= 1;

            matchmakingConfigCache.put(guildId, config);
        }

        statement.close();

        return affected;
    }

    /**
     * @return true if a row was affected
     */
    public boolean updateMatchmakingRole(long guildId, long lfgRoleId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("UPDATE unranked_matchmaking_configs SET lfg_role_id = ? WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        statement.setLong(1, lfgRoleId);
        statement.setLong(2, guildId);

        boolean affected;
        synchronized (matchmakingConfigCache) {
            affected = statement.executeUpdate() >= 1;

            MatchmakingConfig cachedConfig = matchmakingConfigCache.get(guildId);
            if (cachedConfig != null)
                matchmakingConfigCache.put(guildId, new MatchmakingConfig(lfgRoleId, cachedConfig.getChannelId()));
        }

        statement.close();

        return affected;
    }

    /**
     * @return true if a row was affected
     */
    public boolean updateMatchmakingChannel(long guildId, @Nullable Long channelId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("UPDATE unranked_matchmaking_configs SET channel_id = ? WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        if (channelId == null) statement.setNull(1, Types.BIGINT);
        else statement.setLong(1, channelId);
        statement.setLong(2, guildId);

        boolean affected;
        synchronized (matchmakingConfigCache) {
            affected = statement.executeUpdate() >= 1;

            MatchmakingConfig cachedConfig = matchmakingConfigCache.get(guildId);
            if (cachedConfig != null)
                matchmakingConfigCache.put(guildId, new MatchmakingConfig(cachedConfig.getLfgRoleId(), channelId));
        }

        statement.close();

        return affected;
    }

    /**
     * @return true if a row was affected
     */
    public boolean deleteMatchmakingConfig(long guildId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("DELETE FROM unranked_matchmaking_configs WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        statement.setLong(1, guildId);

        boolean affected = statement.executeUpdate() >= 1;

        statement.close();

        // About race condition: We are fine with too little in cache
        matchmakingConfigCache.remove(guildId);

        return affected;
    }

    public void shutdown() throws SQLException {
        connection.close();
    }

    public static class MatchmakingConfig {
        private final long lfgRoleId;
        @Nullable
        private final Long channelId;

        public MatchmakingConfig(long lfgRoleId, @Nullable Long channelId) {
            this.lfgRoleId = lfgRoleId;
            this.channelId = channelId;
        }

        public long getLfgRoleId() {
            return lfgRoleId;
        }

        @Nullable
        public Long getChannelId() {
            return channelId;
        }
    }
}
