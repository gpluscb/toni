package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;
import java.util.function.Function;

public class PairNonnull<T, U> extends Pair<T, U> {
    public PairNonnull(@Nonnull T t, @Nonnull U u) {
        super(t, u);
        //noinspection ConstantConditions
        if (t == null || u == null) throw new IllegalArgumentException("No values may be null");
    }

    public static <T, U> PairNonnull<T, U> fromPair(@Nonnull Pair<T, U> pair) {
        return new PairNonnull<>(pair.getT(), pair.getU());
    }

    @Override
    @Nonnull
    public T getT() {
        return super.getT();
    }

    @Override
    public void setT(@Nonnull T t) {
        super.setT(t);
    }

    @Override
    @Nonnull
    public U getU() {
        return super.getU();
    }

    @Override
    public void setU(@Nonnull U u) {
        super.setU(u);
    }

    @Nonnull
    @Override
    public <V, W> PairNonnull<V, W> map(@Nonnull Function<T, V> funT, @Nonnull Function<U, W> funU) {
        return PairNonnull.fromPair(super.map(funT, funU));
    }

    @Nonnull
    @Override
    public <V> PairNonnull<V, U> mapT(@Nonnull Function<T, V> fun) {
        return PairNonnull.fromPair(super.mapT(fun));
    }

    @Nonnull
    @Override
    public <W> PairNonnull<T, W> mapU(@Nonnull Function<U, W> fun) {
        return PairNonnull.fromPair(super.mapU(fun));
    }
}
