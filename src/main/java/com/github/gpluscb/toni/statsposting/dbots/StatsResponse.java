package com.github.gpluscb.toni.statsposting.dbots;

public class StatsResponse {
    private final int shardCount;
    private final int guildCount;

    public StatsResponse(int shardCount, int guildCount) {
        this.shardCount = shardCount;
        this.guildCount = guildCount;
    }

    public int getShardCount() {
        return shardCount;
    }

    public int getGuildCount() {
        return guildCount;
    }
}
