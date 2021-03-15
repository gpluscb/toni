package com.github.gpluscb.toni.dbots;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DBotsClient {
    private static final Logger log = LogManager.getLogger(DBotsClient.class);

    @Nonnull
    private final String token;
    private final long id;
    @Nonnull
    private final DBotsService service;

    public DBotsClient(@Nonnull String token, long id) {
        this.token = token;
        this.id = id;

        Executor callbackExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("DBotsClient [%d] Callback-Thread", i));
            }
        });

        Gson gson = new GsonBuilder().create();

        // TODO: Think about executors
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://discord.bots.gg/api/v1/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callbackExecutor(callbackExecutor)
                .build();

        // FIXME: So this takes 5 minutes to shut down, maybe because we post?? Seems to be the good old OkHttp issue
        // Idk how to get the OkHttp from this
        // Maybe ditch retrofit completely and transition to just OkHttp? Should be good enough honestly.
        // Alternatively wait on https://github.com/DV8FromTheWorld/JDA/pull/1512 that should fix it too
        service = retrofit.create(DBotsService.class);
    }

    @Nonnull
    public CompletableFuture<StatsResponse> setStats(int guildCount) {
        JsonObject body = new JsonObject();
        body.addProperty("guildCount", guildCount);

        CompletableFuture<StatsResponse> ret = new CompletableFuture<>();

        service.setStats(token, id, body).enqueue(new Callback<StatsResponse>() {
            @Override
            public void onResponse(@Nonnull Call<StatsResponse> call, @Nonnull Response<StatsResponse> response) {
                if (response.isSuccessful()) ret.complete(response.body());
                else {
                    log.error("Error posting stats: {}", response);
                    ret.completeExceptionally(new IllegalStateException("Error, logged"));
                }
            }

            @Override
            public void onFailure(@Nonnull Call<StatsResponse> call, @Nonnull Throwable t) {
                ret.completeExceptionally(t);
            }
        });

        return ret;
    }
}
