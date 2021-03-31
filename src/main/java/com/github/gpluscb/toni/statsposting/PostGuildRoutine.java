package com.github.gpluscb.toni.statsposting;

import com.github.gpluscb.toni.statsposting.dbots.StatsResponse;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PostGuildRoutine extends ListenerAdapter {
    private static final Logger log = LogManager.getLogger(PostGuildRoutine.class);

    @Nonnull
    private final ScheduledThreadPoolExecutor executer;

    @Nonnull
    private final BotListClient<StatsResponse> dBotsClient;
    @Nonnull
    private final BotListClient<Void> topggClient;
    @Nonnull
    private final ShardManager shardManager;

    @Nonnull
    private final Set<Integer> shardsReady;
    @Nonnull
    private final AtomicBoolean isRunning;

    public PostGuildRoutine(@Nonnull BotListClient<StatsResponse> dBotsClient, @Nonnull BotListClient<Void> topggClient, @Nonnull ShardManager shardManager) {
        executer = new ScheduledThreadPoolExecutor(1);
        executer.setThreadFactory(r -> new Thread(r, "PostGuildRoutine Schedule-Thread"));

        this.dBotsClient = dBotsClient;
        this.topggClient = topggClient;
        this.shardManager = shardManager;

        shardsReady = new HashSet<>();
        isRunning = new AtomicBoolean(false);
    }

    private void init() {
        executer.scheduleAtFixedRate(() -> {
            // We are in far fewer than 2 million guilds, so the cast is safe.
            int guildCount = (int) shardManager.getGuildCache().size();
            dBotsClient.postStats(guildCount).whenComplete((r, t) -> {
                if (t != null) log.catching(t);
                else
                    log.debug("Successful guild stats post to dbots: guilds: {}, shards: {}", r.getGuildCount(), r.getShardCount());
            });
            topggClient.postStats(guildCount).whenComplete((r, t) -> {
                if (t != null) log.catching(t);
                else log.debug("Successful guild stats post to topgg");
            });
        }, 0, 6, TimeUnit.HOURS);
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        int shardsReadyCount;
        synchronized (shardsReady) {
            shardsReady.add(event.getJDA().getShardInfo().getShardId());
            shardsReadyCount = shardsReady.size();
        }

        int shardsTotal = shardManager.getShardsTotal();

        if (shardsReadyCount > shardsTotal) {
            log.error("shardsReadyCount ({}) > shardsTotal ({})", shardsReadyCount, shardsTotal);
        } else if (shardsReadyCount == shardsTotal && !isRunning.getAndSet(true)) {
            init();
        }
    }

    public void shutdown() {
        executer.shutdownNow();
    }
}
