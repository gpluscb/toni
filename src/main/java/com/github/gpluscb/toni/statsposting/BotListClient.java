package com.github.gpluscb.toni.statsposting;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public interface BotListClient<T> {
    @Nonnull
    CompletableFuture<T> postStats(long guildCount);
}
