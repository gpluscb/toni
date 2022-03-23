package com.github.gpluscb.toni.smashset;

import com.github.gpluscb.toni.util.MiscUtil;

import javax.annotation.Nonnull;

public record Stage(int stageId, long stageEmoteId, @Nonnull String name) {
    @SuppressWarnings("ConstantConditions")
    public void check() {
        if (name == null) throw new IllegalStateException("Name may not be null");
    }

    @Nonnull
    public String getDisplayName() {
        return String.format("%s(%s)", MiscUtil.mentionEmote(stageEmoteId), name);
    }
}
