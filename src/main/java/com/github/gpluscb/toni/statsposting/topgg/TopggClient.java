package com.github.gpluscb.toni.statsposting.topgg;

import com.github.gpluscb.toni.statsposting.BotListClient;
import com.github.gpluscb.toni.statsposting.dbots.DBotsClient;
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

public class TopggClient implements BotListClient<Void> {
    private static final Logger log = LogManager.getLogger(DBotsClient.class);

    @Nonnull
    private final String token;
    private final long id;
    @Nonnull
    private final TopggService service;

    public TopggClient(@Nonnull String token, @Nonnull OkHttpClient client, long id) {
        this.token = token;
        this.id = id;

        Executor callbackExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("TopggClient [%d] Callback-Thread", i));
            }
        });

        Gson gson = new GsonBuilder().create();

        // TODO: Think about executors
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://top.gg/api/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callbackExecutor(callbackExecutor)
                .client(client)
                .build();

        service = retrofit.create(TopggService.class);
    }

    @Nonnull
    public CompletableFuture<Void> postStats(long guildCount) {
        JsonObject body = new JsonObject();
        body.addProperty("server_count", guildCount);

        CompletableFuture<Void> ret = new CompletableFuture<>();

        service.postStats(token, id, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@Nonnull Call<JsonObject> call, @Nonnull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    ret.complete(null);
                } else {
                    log.error("Error posting stats: {}", response);
                    ret.completeExceptionally(new IllegalStateException("Error, logged"));
                }
            }

            @Override
            public void onFailure(@Nonnull Call<JsonObject> call, @Nonnull Throwable t) {
                ret.completeExceptionally(t);
            }
        });

        return ret;
    }
}
