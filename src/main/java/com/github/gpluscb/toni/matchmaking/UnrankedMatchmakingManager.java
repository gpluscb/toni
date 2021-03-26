package com.github.gpluscb.toni.matchmaking;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UnrankedMatchmakingManager {
    @Nonnull
    private final Connection connection;
    @Nonnull
    private final Map<Long, UnrankedGuildMatchmakingConfig> matchmakingConfigCache;

    public UnrankedMatchmakingManager(@Nonnull String dbLocation) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        matchmakingConfigCache = Collections.synchronizedMap(new HashMap<>());
    }

    @Nullable
    public UnrankedGuildMatchmakingConfig loadMatchmakingConfig(long guildId) throws SQLException {
        UnrankedGuildMatchmakingConfig cached = matchmakingConfigCache.get(guildId);
        if (cached != null) return cached;

        PreparedStatement statement = connection.prepareStatement("SELECT lfg_role_id, channel_id FROM matchmaking_configs WHERE guild_id = ?");
        statement.setQueryTimeout(10);

        statement.setLong(1, guildId);

        ResultSet rs = statement.executeQuery();

        long lfgRoleId = rs.getLong("lfg_role_id");
        Long channelId = rs.getLong("channel_id");
        if (rs.wasNull()) channelId = null;

        return new UnrankedGuildMatchmakingConfig(lfgRoleId, channelId);
    }

    public boolean storeMatchmakingConfig(long guildId, @Nonnull UnrankedGuildMatchmakingConfig config) throws SQLException {
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

        return affected;
    }

    /**
     * @return true if a row was affected
     */
    public boolean updateMatchmakingConfig(long guildId, @Nonnull UnrankedGuildMatchmakingConfig config) throws SQLException {
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

            UnrankedGuildMatchmakingConfig cachedConfig = matchmakingConfigCache.get(guildId);
            if (cachedConfig != null)
                matchmakingConfigCache.put(guildId, new UnrankedGuildMatchmakingConfig(lfgRoleId, cachedConfig.getChannelId()));
        }

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

            UnrankedGuildMatchmakingConfig cachedConfig = matchmakingConfigCache.get(guildId);
            if (cachedConfig != null)
                matchmakingConfigCache.put(guildId, new UnrankedGuildMatchmakingConfig(cachedConfig.getLfgRoleId(), channelId));
        }

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

        // About race condition: We are fine with too little in cache
        matchmakingConfigCache.remove(guildId);

        return affected;
    }

    public static class UnrankedGuildMatchmakingConfig {
        private final long lfgRoleId;
        @Nullable
        private final Long channelId;

        public UnrankedGuildMatchmakingConfig(long lfgRoleId, @Nullable Long channelId) {
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
