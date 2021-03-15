package com.github.gpluscb.toni.routines;

import com.github.gpluscb.toni.dbots.DBotsClient;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.discordbots.api.client.DiscordBotListAPI;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

public class PostGuildRoutine {
    private static final Logger log = LogManager.getLogger(PostGuildRoutine.class);

    @Nonnull
    private final ScheduledThreadPoolExecutor executer;

    public PostGuildRoutine(@Nonnull DBotsClient dBotsClient, @Nonnull DiscordBotListAPI topggClient, @Nonnull ShardManager jda) {
        executer = new ScheduledThreadPoolExecutor(1);
        executer.setThreadFactory(r -> new Thread(r, "PostGuildRoutine Schedule-Thread"));

        executer.scheduleAtFixedRate(() -> {
            // We are in far fewer than 2 million guilds, so the cast is safe.
            int guildCount = (int) jda.getGuildCache().size();
            guildCount = 28;// FIXME: hardcoded
            dBotsClient.setStats(guildCount).whenComplete((r, t) -> {
                if (t != null) log.catching(t);
                else
                    log.debug("Successful guild stats post to dbots: guilds: {}, shards: {}", r.getGuildCount(), r.getShardCount());
            });
            topggClient.setStats(guildCount).whenComplete((_v, t) -> {
                if (t != null) log.catching(t);
                else log.debug("Successful guild stats post to topgg");
            });
        }, 0, 6, TimeUnit.HOURS);
    }

    public void shutdown() {
        executer.shutdownNow();
    }
}
