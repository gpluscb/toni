package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;

public record SetScore(int player1Score, int player2Score) {
    /**
     * Ratio of games player 1 won.
     * Always between 0 and 1.
     */
    public double player1Ratio() {
        return player1Score / ((double) (player1Score + player2Score));
    }

    /**
     * Ratio of games player 1 won.
     * Always between 0 and 1.
     */
    public double player2Ratio() {
        return player2Score / ((double) (player1Score + player2Score));
    }

    @Nonnull
    public Player getWinner() {
        return player1Score > player2Score ?
                Player.PLAYER1
                : Player.PLAYER2;
    }
}
