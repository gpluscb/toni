package com.github.gpluscb.toni.statsposting.topgg;

import com.github.gpluscb.toni.statsposting.BotListClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class TopggClientMock implements BotListClient<Void> {
    private static final Logger log = LogManager.getLogger(TopggClientMock.class);

    @Nonnull
    @Override
    public CompletableFuture<Void> postStats(long guildCount) {
        log.debug("TopggClientMock postsStats: guild count: {}", guildCount);
        return CompletableFuture.completedFuture(null);
    }
}
