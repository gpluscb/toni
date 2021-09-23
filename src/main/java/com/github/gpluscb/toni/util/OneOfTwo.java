package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

// All I want for christmas is algebraic types in Java
public class OneOfTwo<T, U> {
    @Nullable
    private final T t;
    @Nullable
    private final U u;

    public OneOfTwo(@Nullable T t, @Nullable U u) {
        if ((t == null) == (u == null)) throw new IllegalArgumentException("One of two must be null");
        this.t = t;
        this.u = u;
    }

    @Nonnull
    public static <T, U> OneOfTwo<T, U> ofT(@Nonnull T t) {
        return new OneOfTwo<>(t, null);
    }

    @Nonnull
    public static <T, U> OneOfTwo<T, U> ofU(@Nonnull U u) {
        return new OneOfTwo<>(null, u);
    }

    @Nonnull
    public OneOfTwo<T, U> onT(@Nonnull Consumer<T> consumer) {
        if (t != null) consumer.accept(t);
        return this;
    }

    @Nonnull
    public OneOfTwo<T, U> onU(@Nonnull Consumer<U> consumer) {
        if (u != null) consumer.accept(u);
        return this;
    }

    @Nonnull
    public <V> OneOfTwo<V, U> mapT(@Nonnull Function<T, V> map) {
        return map(t -> ofT(map.apply(t)), OneOfTwo::ofU);
    }

    @Nonnull
    public <V> OneOfTwo<T, V> mapU(@Nonnull Function<U, V> map) {
        return map(OneOfTwo::ofT, u -> ofU(map.apply(u)));
    }

    @Nonnull
    public <V> V map(@Nonnull Function<T, V> onT, @Nonnull Function<U, V> onU) {
        return t != null ? onT.apply(t) : onU.apply(u);
    }

    public boolean isT() {
        return t != null;
    }

    public boolean isU() {
        return u != null;
    }

    @Nonnull
    public Optional<T> getT() {
        return Optional.ofNullable(t);
    }

    @Nonnull
    public Optional<U> getU() {
        return Optional.ofNullable(u);
    }

    @Nonnull
    public T getTOrThrow() {
        if (t != null) return t;
        throw new IllegalStateException("Called getTOrThrow when this is U");
    }

    @Nonnull
    public U getUOrThrow() {
        if (u != null) return u;
        throw new IllegalStateException("Called getUOrThrow when this is T");
    }

    @Override
    public String toString() {
        return "OneOfTwo{" +
                "t=" + t +
                ", u=" + u +
                '}';
    }
}
