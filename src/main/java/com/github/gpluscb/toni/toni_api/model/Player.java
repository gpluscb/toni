package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public enum Player {
    PLAYER1,
    PLAYER2;

    @Nonnull
    @CheckReturnValue
    public Player invert() {
        return switch (this) {
            case PLAYER1 -> PLAYER2;
            case PLAYER2 -> PLAYER1;
        };
    }
}
