package com.github.gpluscb.toni.toni_api;

import com.github.gpluscb.toni.toni_api.model.GuildUserResponse;
import com.github.gpluscb.toni.toni_api.model.SetIdResponse;
import com.github.gpluscb.toni.toni_api.model.SmashSet;
import com.github.gpluscb.toni.util.Pair;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ToniApiClient {
    private static final Logger log = LogManager.getLogger(ToniApiClient.class);

    @Nonnull
    private final ToniApiService service;

    public ToniApiClient(HttpUrl baseUrl, String token, @Nonnull OkHttpClient client, @Nonnull Gson gson) {
        Executor callbackExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("UltimateframedataClient [%d] Callback-Thread", i));
            }
        });

        Gson customGson = gson.newBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .create();

        OkHttpClient customClient = client.newBuilder()
                .authenticator((route, response) -> {
                    // Based on https://square.github.io/okhttp/recipes/#handling-authentication-kt-java
                    if (response.request().header("Authorization") != null) {
                        return null; // Give up, we've already attempted to authenticate.
                    }

                    return response.request().newBuilder()
                            .header("Authorization", String.format("Bearer: %s", token))
                            .build();
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .callbackExecutor(callbackExecutor)
                .addConverterFactory(GsonConverterFactory.create(customGson))
                .client(customClient)
                .build();

        service = retrofit.create(ToniApiService.class);
    }

    private <T> Pair<CompletableFuture<T>, Callback<T>> createCallback() {
        CompletableFuture<T> fut = new CompletableFuture<>();

        return new Pair<>(fut, new Callback<>() {
            @Override
            public void onResponse(@Nonnull Call<T> call, @Nonnull Response<T> response) {
                if (response.isSuccessful()) fut.complete(response.body());
                else if (response.code() == 404) fut.complete(null);
                else {
                    try (ResponseBody body = response.errorBody()) {
                        log.error("ToniApiClient: status {} {}: {}", response.code(), response.message(), body == null ? "missing body" : body.string());
                    } catch (IOException e) {
                        log.error("ToniApiClient: status {} {}: non-string body", response.code(), response.message());
                    }
                    fut.completeExceptionally(new IllegalStateException("Server error"));
                }
            }

            @Override
            public void onFailure(@Nonnull Call<T> call, @Nonnull Throwable t) {
                fut.completeExceptionally(t);
            }
        });
    }

    @Nonnull
    public CompletableFuture<GuildUserResponse> guildUserRanking(long guildId, long userId) {
        Pair<CompletableFuture<GuildUserResponse>, Callback<GuildUserResponse>> pair = createCallback();
        service.guildUserRanking(guildId, userId).enqueue(pair.getU());
        return pair.getT();
    }

    @Nonnull
    public CompletableFuture<List<GuildUserResponse>> guildRankings(long guildId) {
        Pair<CompletableFuture<List<GuildUserResponse>>, Callback<List<GuildUserResponse>>> pair = createCallback();
        service.guildRankings(guildId).enqueue(pair.getU());
        return pair.getT();
    }

    @Nonnull
    public CompletableFuture<List<GuildUserResponse>> userGuilds(long userId) {
        Pair<CompletableFuture<List<GuildUserResponse>>, Callback<List<GuildUserResponse>>> pair = createCallback();
        service.userGuilds(userId).enqueue(pair.getU());
        return pair.getT();
    }

    @Nonnull
    public CompletableFuture<SmashSet> ratedSet(long setId) {
        Pair<CompletableFuture<SmashSet>, Callback<SmashSet>> pair = createCallback();
        service.ratedSet(setId).enqueue(pair.getU());
        return pair.getT();
    }

    @Nonnull
    public CompletableFuture<List<SmashSet>> userSets(long userId) {
        Pair<CompletableFuture<List<SmashSet>>, Callback<List<SmashSet>>> pair = createCallback();
        service.userSets(userId).enqueue(pair.getU());
        return pair.getT();
    }

    @Nonnull
    public CompletableFuture<SetIdResponse> registerRatedSet(@Nonnull SmashSet set) {
        Pair<CompletableFuture<SetIdResponse>, Callback<SetIdResponse>> pair = createCallback();
        service.registerRatedSet(set).enqueue(pair.getU());
        return pair.getT();
    }
}
