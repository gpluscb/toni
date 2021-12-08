package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Function;

// TODO: Read into records, maybe they'd make this obsolete idk
public class Pair<T, U> {
    private T t;
    private U u;

    public Pair(T t, U u) {
        this.t = t;
        this.u = u;
    }

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }

    public U getU() {
        return u;
    }

    public void setU(U u) {
        this.u = u;
    }

    @Nonnull
    public <V, W> Pair<V, W> map(@Nonnull Function<T, V> funT, @Nonnull Function<U, W> funU) {
        return new Pair<>(funT.apply(t), funU.apply(u));
    }

    @Nonnull
    public <V> Pair<V, U> mapT(@Nonnull Function<T, V> fun) {
        return map(fun, Function.identity());
    }

    @Nonnull
    public <W> Pair<T, W> mapU(@Nonnull Function<U, W> fun) {
        return map(Function.identity(), fun);
    }

    @Override
    public String toString() {
        return "Pair{" +
                "t=" + t +
                ", u=" + u +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(t, pair.t) && Objects.equals(u, pair.u);
    }

    @Override
    public int hashCode() {
        return Objects.hash(t, u);
    }
}
