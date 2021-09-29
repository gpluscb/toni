package com.github.gpluscb.toni.matchmaking;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class UnrankedManager {
    @Nonnull
    private final MongoCollection<UnrankedMatchmakingConfig> guilds;

    public UnrankedManager(@Nonnull MongoClient client) {
        guilds = client.getDatabase("unrankedMatchmakingConfigs")
                .getCollection("guilds", UnrankedMatchmakingConfig.class);
    }

    /**
     * {@link CompletableFuture} may complete with null
     */
    @Nonnull
    public CompletableFuture<UnrankedMatchmakingConfig> loadMatchmakingConfig(long guildId) {
        CompletableFuture<UnrankedMatchmakingConfig> ret = new CompletableFuture<>();

        guilds.find(Filters.eq("guildId", guildId)).first((r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(r);
        });

        return ret;
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public CompletableFuture<Void> storeMatchmakingConfig(@Nonnull UnrankedMatchmakingConfig config) {
        CompletableFuture<Void> ret = new CompletableFuture<>();

        guilds.insertOne(config, (r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(null);
        });

        return ret;
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public CompletableFuture<UpdateResult> updateMatchmakingConfig(@Nonnull UnrankedMatchmakingConfig config) {
        CompletableFuture<UpdateResult> ret = new CompletableFuture<>();

        guilds.replaceOne(Filters.eq("id", config.guildId), config, (r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(r);
        });

        return ret;
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public CompletableFuture<UpdateResult> updateMatchmakingRole(long guildId, long lfgRoleId) {
        CompletableFuture<UpdateResult> ret = new CompletableFuture<>();

        guilds.updateOne(Filters.eq("id", guildId), new Document("$set", new Document("lfgRoleId", lfgRoleId)), (r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(r);
        });

        return ret;
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public CompletableFuture<UpdateResult> updateMatchmakingChannel(long guildId, @Nullable Long channelId) {
        CompletableFuture<UpdateResult> ret = new CompletableFuture<>();

        guilds.updateOne(Filters.eq("id", guildId), new Document("$set", new Document("channelId", channelId)), (r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(r);
        });

        return ret;
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    public CompletableFuture<DeleteResult> deleteMatchmakingConfig(long guildId) {
        CompletableFuture<DeleteResult> ret = new CompletableFuture<>();

        guilds.deleteOne(Filters.eq("id", guildId), (r, t) -> {
            if (t != null) ret.completeExceptionally(t);
            else ret.complete(r);
        });

        return ret;
    }

    public static class UnrankedMatchmakingConfig {
        private final long guildId;
        private final long lfgRoleId;
        @Nullable
        private final Long channelId;

        public UnrankedMatchmakingConfig(long guildId, long lfgRoleId, @Nullable Long channelId) {
            this.guildId = guildId;
            this.lfgRoleId = lfgRoleId;
            this.channelId = channelId;
        }

        public long getGuildId() {
            return guildId;
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
