package com.github.gpluscb.toni.util.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ShardsLoadListener extends ListenerAdapter {
    private static final Logger log = LogManager.getLogger(ShardsLoadListener.class);

    @Nonnull
    private final Set<Integer> shardsReady;
    @Nonnull
    private final AtomicBoolean isRunning;

    @Nonnull
    private final Consumer<JDA> onShardLoad;
    @Nonnull
    private final Consumer<ShardManager> onShardsLoaded;

    public ShardsLoadListener(@Nonnull Consumer<JDA> onShardLoad, @Nonnull Consumer<ShardManager> onShardsLoaded) {
        this.onShardLoad = onShardLoad;
        this.onShardsLoaded = onShardsLoaded;

        shardsReady = new HashSet<>();
        isRunning = new AtomicBoolean(false);
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        JDA jda = event.getJDA();

        onShardLoad.accept(jda);

        ShardManager shardManager = jda.getShardManager();
        if (shardManager == null) throw new IllegalStateException("JDA not managed by ShardManager");

        int shardsReadyCount;
        synchronized (shardsReady) {
            shardsReady.add(event.getJDA().getShardInfo().getShardId());
            shardsReadyCount = shardsReady.size();
        }

        int shardsTotal = shardManager.getShardsTotal();

        if (shardsReadyCount > shardsTotal) {
            log.error("shardsReadyCount ({}) > shardsTotal ({})", shardsReadyCount, shardsTotal);
        } else if (shardsReadyCount == shardsTotal && !isRunning.getAndSet(true)) {
            onShardsLoaded.accept(shardManager);
        }
    }
}
