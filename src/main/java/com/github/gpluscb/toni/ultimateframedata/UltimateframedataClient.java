package com.github.gpluscb.toni.ultimateframedata;

import com.github.gpluscb.toni.util.FailLogger;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
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

public class UltimateframedataClient {
    @Nonnull
    private final UltimateframedataService service;

    public UltimateframedataClient(@Nonnull OkHttpClient client) {
        Executor callbackExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("UltimateframedataClient [%d] Callback-Thread", i));
            }
        });

        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://127.0.0.1:8080/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callbackExecutor(callbackExecutor)
                .client(client)
                .build();

        service = retrofit.create(UltimateframedataService.class);
    }

    @Nonnull
    public CompletableFuture<CharacterData> getCharacter(long id) {
        CompletableFuture<CharacterData> ret = new CompletableFuture<>();

        service.getCharacter(id).enqueue(FailLogger.logFail(new Callback<CharacterData>() {
            @Override
            public void onResponse(@Nonnull Call<CharacterData> call, @Nonnull Response<CharacterData> response) {
                if (response.isSuccessful()) ret.complete(response.body());
                else if (response.code() == 404) ret.complete(null);
                else ret.completeExceptionally(new IllegalStateException("Server error"));
            }

            @Override
            public void onFailure(@Nonnull Call<CharacterData> call, @Nonnull Throwable t) {
                ret.completeExceptionally(t);
            }
        }));

        return ret;
    }
}
