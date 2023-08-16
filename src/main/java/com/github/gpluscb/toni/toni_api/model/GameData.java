package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public record GameData(@Nullable Conflict conflict, @Nonnull Player player, @Nullable Short player1CharId,
                       @Nullable Short player2CharId, @Nullable
                       Set<Short> stageBanIds, @Nullable Short stageId) {
}
