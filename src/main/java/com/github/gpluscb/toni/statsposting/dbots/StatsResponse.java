package com.github.gpluscb.toni.statsposting.dbots;

public class StatsResponse {
    private final int shardCount;
    private final long guildCount;

    public StatsResponse(int shardCount, long guildCount) {
        this.shardCount = shardCount;
        this.guildCount = guildCount;
    }

    public int getShardCount() {
        return shardCount;
    }

    public long getGuildCount() {
        return guildCount;
    }
}
