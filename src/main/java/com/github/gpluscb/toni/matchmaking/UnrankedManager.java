package com.github.gpluscb.toni.matchmaking;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class UnrankedManager {
    @Nonnull
    private final MongoCollection<UnrankedMatchmakingConfig> guilds;

    public UnrankedManager(@Nonnull MongoClient client) {
        CodecProvider pojoProvider = PojoCodecProvider.builder().register(UnrankedMatchmakingConfig.class).build();
        CodecRegistry registry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(pojoProvider));

        guilds = client.getDatabase("unrankedMatchmakingConfigs")
                .getCollection("guilds", UnrankedMatchmakingConfig.class)
                .withCodecRegistry(registry);
    }

    /**
     * {@link CompletableFuture} may complete with null
     */
    @Nonnull
    public Mono<UnrankedMatchmakingConfig> loadMatchmakingConfig(long guildId) {
        return Mono.from(guilds.find(Filters.eq("guildId", guildId)).first());
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public Mono<InsertOneResult> storeMatchmakingConfig(@Nonnull UnrankedMatchmakingConfig config) {
        return Mono.from(guilds.insertOne(config));
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public Mono<UpdateResult> updateMatchmakingConfig(@Nonnull UnrankedMatchmakingConfig config) {
        return Mono.from(guilds.replaceOne(Filters.eq("id", config.getGuildId()), config));
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public Mono<UpdateResult> updateMatchmakingRole(long guildId, long lfgRoleId) {
        return Mono.from(guilds.updateOne(Filters.eq("id", guildId), new Document("$set", new Document("lfgRoleId", lfgRoleId))));
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    @Nonnull
    public Mono<UpdateResult> updateMatchmakingChannel(long guildId, @Nullable Long channelId) {
        return Mono.from(guilds.updateOne(Filters.eq("id", guildId), new Document("$set", new Document("channelId", channelId))));
    }

    /**
     * @throws com.mongodb.MongoWriteException        returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoWriteConcernException returned via the {@link CompletableFuture}
     * @throws com.mongodb.MongoException             returned via the {@link CompletableFuture}
     */
    public Mono<DeleteResult> deleteMatchmakingConfig(long guildId) {
        return Mono.from(guilds.deleteOne(Filters.eq("id", guildId)));
    }

    // TODO: The deserializer thingy
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
