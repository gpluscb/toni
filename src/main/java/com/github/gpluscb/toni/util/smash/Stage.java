package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;

public class Stage {
    @Nonnull
    private final String name;

    public Stage(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getName() {
        return name;
    }
}
