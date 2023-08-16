package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;

public record Conflict(boolean bothClaimedWin, @Nonnull ConflictResolution resolution) {
}
