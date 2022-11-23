package com.github.gpluscb.toni.toni_api.model;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

public record SmashSet(long guildId, long player1, long player2, short rulesetId, int firstToWhatScore, boolean doRps,
                       @Nonnull Player rpsWinner, @Nonnull Player firstStageStriker,
                       @Nonnull List<Set<Short>> stageStrikingIdxHistory, @Nonnull List<GameData> games) {
}
