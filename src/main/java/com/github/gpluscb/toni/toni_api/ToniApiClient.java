package com.github.gpluscb.toni.toni_api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ToniApiClient {
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
}
