package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;

public class Stage {
    private final int stageId;
    private final long stageEmoteId;
    @Nonnull
    private final String name;

    public Stage(int stageId, long stageEmoteId, @Nonnull String name) {
        this.stageId = stageId;
        this.stageEmoteId = stageEmoteId;
        this.name = name;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public int getStageId() {
        return stageId;
    }

    public long getStageEmoteId() {
        return stageEmoteId;
    }
}
