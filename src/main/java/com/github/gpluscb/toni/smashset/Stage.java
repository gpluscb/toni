package com.github.gpluscb.toni.smashset;

import javax.annotation.Nonnull;

public record Stage(int stageId, long stageEmoteId, @Nonnull String name) {
    @Nonnull
    public String getDisplayName() {
        // TODO: Change once we have emotes
        return name();
    }
}
