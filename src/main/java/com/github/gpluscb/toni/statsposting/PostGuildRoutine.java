package com.github.gpluscb.toni.statsposting;

import com.github.gpluscb.toni.statsposting.dbots.DBotsClient;
import com.github.gpluscb.toni.statsposting.topgg.TopggClient;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

public class PostGuildRoutine {
    private static final Logger log = LogManager.getLogger(PostGuildRoutine.class);

    @Nonnull
    private final ScheduledThreadPoolExecutor executer;

    public PostGuildRoutine(@Nonnull DBotsClient dBotsClient, @Nonnull TopggClient topggClient, @Nonnull ShardManager shardManager) {
        executer = new ScheduledThreadPoolExecutor(1);
        executer.setThreadFactory(r -> new Thread(r, "PostGuildRoutine Schedule-Thread"));

        executer.scheduleAtFixedRate(() -> {
            // We are in far fewer than 2 million guilds, so the cast is safe.
            int guildCount = (int) shardManager.getGuildCache().size();
            guildCount = 39;// FIXME: hardcoded
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

    public void shutdown() {
        executer.shutdownNow();
    }
}
