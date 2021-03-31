package com.github.gpluscb.toni.statsposting.dbots;

import com.github.gpluscb.toni.statsposting.BotListClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class DBotsClientMock implements BotListClient<StatsResponse> {
    private static final Logger log = LogManager.getLogger(DBotsClientMock.class);

    @Nonnull
    @Override
    public CompletableFuture<StatsResponse> postStats(long guildCount) {
        log.debug("DBotsClientMock postsStats: guild count: {}", guildCount);
        return CompletableFuture.completedFuture(new StatsResponse(1, guildCount));
    }
}
