package com.github.gpluscb.toni.statsposting.dbots;

import com.github.gpluscb.toni.statsposting.BotListClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
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

public class DBotsClient implements BotListClient<StatsResponse> {
    private static final Logger log = LogManager.getLogger(DBotsClient.class);

    @Nonnull
    private final String token;
    private final long id;
    @Nonnull
    private final DBotsService service;

    public DBotsClient(@Nonnull String token, @Nonnull OkHttpClient client, long id) {
        this.token = token;
        this.id = id;

        Executor callbackExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("DBotsClient [%d] Callback-Thread", i++));
            }
        });

        Gson gson = new GsonBuilder().create();

        // TODO: Think about executors
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://discord.bots.gg/api/v1/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callbackExecutor(callbackExecutor)
                .client(client)
                .build();

        service = retrofit.create(DBotsService.class);
    }

    @Override
    @Nonnull
    public CompletableFuture<StatsResponse> postStats(long guildCount) {
        JsonObject body = new JsonObject();
        body.addProperty("guildCount", guildCount);

        CompletableFuture<StatsResponse> ret = new CompletableFuture<>();

        service.postStats(token, id, body).enqueue(new Callback<StatsResponse>() {
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
