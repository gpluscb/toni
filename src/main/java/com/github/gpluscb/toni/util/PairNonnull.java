package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;

public class PairNonnull<T, U> extends Pair<T, U> {
    public PairNonnull(@Nonnull T t, @Nonnull U u) {
        super(t, u);
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
}
