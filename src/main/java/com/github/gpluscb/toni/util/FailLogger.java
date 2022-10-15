package com.github.gpluscb.toni.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Call;
import retrofit2.Response;

import javax.annotation.Nonnull;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO: Maaaybe replace with DefaultUncaughtExceptionHandler oops that exists
public class FailLogger {
    private static final Logger log = LogManager.getLogger(FailLogger.class);

    // TODO: Should we log and swallow instead?
    @Nonnull
    public static Runnable logFail(@Nonnull Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                log.catching(t);
                throw t;
            }
        };
    }

    @Nonnull
    public static <T> Consumer<T> logFail(@Nonnull Consumer<T> consumer) {
        return arg -> {
            try {
                consumer.accept(arg);
            } catch (Throwable t) {
                log.catching(t);
                throw t;
            }
        };
    }

    @Nonnull
    public static <T> retrofit2.Callback<T> logFail(@Nonnull retrofit2.Callback<T> callback) {
        return new retrofit2.Callback<>() {
            @Override
            public void onResponse(@Nonnull Call<T> call, @Nonnull Response<T> response) {
                try {
                    callback.onResponse(call, response);
                } catch (Throwable t) {
                    log.catching(t);
                    throw t;
                }
            }

            @Override
            public void onFailure(@Nonnull Call<T> call, @Nonnull Throwable t) {
                try {
                    callback.onFailure(call, t);
                } catch (Throwable t1) {
                    log.catching(t1);
                    throw t1;
                }
            }
        };
    }

    @Nonnull
    public static <T, U> BiConsumer<T, U> logFail(@Nonnull BiConsumer<T, U> biConsumer) {
        return (arg1, arg2) -> {
            try {
                biConsumer.accept(arg1, arg2);
            } catch (Throwable t) {
                log.catching(t);
                throw t;
            }
        };
    }

    @Nonnull
    public static <T, R> Function<T, R> logFail(@Nonnull Function<T, R> function) {
        return arg -> {
            try {
                return function.apply(arg);
            } catch (Throwable t) {
                log.catching(t);
                throw t;
            }
        };
    }

    @Nonnull
    public static <T, U, R> BiFunction<T, U, R> logFail(@Nonnull BiFunction<T, U, R> biFunction) {
        return (arg1, arg2) -> {
            try {
                return biFunction.apply(arg1, arg2);
            } catch (Throwable t) {
                log.catching(t);
                throw t;
            }
        };
    }
}

