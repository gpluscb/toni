package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record ConflictResolution(@Nonnull Player wrongfulPlayer, @Nullable long interveningMod) {
}
