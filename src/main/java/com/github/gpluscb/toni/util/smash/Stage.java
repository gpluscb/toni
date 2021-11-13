package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;

public record Stage(int stageId, long stageEmoteId, @Nonnull String name) {
    @Nonnull
    public String getDisplayName() {
        // TODO: Change once we have emotes
        return name();
    }
}
