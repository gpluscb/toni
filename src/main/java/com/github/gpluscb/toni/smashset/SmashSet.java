package com.github.gpluscb.toni.smashset;

import com.github.gpluscb.toni.util.OneOfTwo;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SmashSet {
    // TODO: good toString

    @Nonnull
    private final Ruleset ruleset;
    private final int firstToWhatScore;
    private final boolean doRPS;
    @Nullable
    private Player rpsWinner;
    @Nullable
    private Player firstStageStriker;
    @Nonnull
    private final List<Set<Integer>> stageStrikingIdHistory;
    @Nullable
    private SetState state;
    @Nonnull
    private final List<GameData> games;

    public SmashSet(@Nonnull Ruleset ruleset, int firstToWhatScore, boolean doRPS) {
        this.ruleset = ruleset;
        this.firstToWhatScore = firstToWhatScore;
        this.doRPS = doRPS;
        firstStageStriker = null;
        stageStrikingIdHistory = new ArrayList<>(ruleset.starterStrikePattern().length);
        state = null;
        games = new ArrayList<>();

        Integer maximumFirstToWhatScore = ruleset.getMaximumFirstToWhatScore();
        if (maximumFirstToWhatScore != null && firstToWhatScore > maximumFirstToWhatScore)
            throw new IllegalArgumentException("The DSR variation of the ruleset combined with this stage list does not allow for a set this long");
    }

    @Nonnull
    public synchronized OneOfTwo<SetDoubleBlindState, SetRPSState> startSetWithRPS() {
        if (state != null) throw new IllegalStateException("Set already started");
        if (!doRPS) throw new IllegalStateException("startSetWithRPS called on no RPS set");

        GameData firstGame = new GameData(false);
        games.add(firstGame);

        if (ruleset.blindPickBeforeStage()) {
            return OneOfTwo.ofT(switchState(new SetDoubleBlindState(firstGame)));
        } else {
            return OneOfTwo.ofU(switchState(new SetRPSState(firstGame)));
        }
    }

    @Nonnull
    public synchronized OneOfTwo<SetDoubleBlindState, SetStarterStrikingState> startSetNoRPS(@Nonnull Player firstStageStriker) {
        if (state != null) throw new IllegalStateException("Set already started");
        if (doRPS) throw new IllegalStateException("startSetNoRPS called on RPS set");

        this.firstStageStriker = firstStageStriker;

        GameData firstGame = new GameData(false);
        games.add(firstGame);

        if (ruleset.blindPickBeforeStage()) {
            return OneOfTwo.ofT(switchState(new SetDoubleBlindState(firstGame)));
        } else {
            return OneOfTwo.ofU(switchState(new SetStarterStrikingState(firstGame)));
        }
    }

    public int getFirstToWhatScore() {
        return firstToWhatScore;
    }

    public int getBestOfWhatScore() {
        return firstToWhatScore * 2 - 1;
    }

    @Nonnull
    public List<GameData> getGames() {
        return games;
    }

    @Nonnull
    public List<Set<Integer>> getStageStrikingIdHistory() {
        return stageStrikingIdHistory;
    }

    @Nonnull
    public Ruleset getRuleset() {
        return ruleset;
    }

    @Nullable
    public Player getRpsWinner() {
        return rpsWinner;
    }

    @Nullable
    public Player getFirstStageStriker() {
        return firstStageStriker;
    }

    public boolean isCompleted() {
        return state instanceof SetCompletedState;
    }

    @Nonnull
    private <T extends SetState> T switchState(@Nonnull T state) {
        this.state = state;
        return state;
    }

    public enum Player {
        PLAYER1,
        PLAYER2;

        @CheckReturnValue
        @Nonnull
        public Player invert() {
            return switch (this) {
                case PLAYER1 -> PLAYER2;
                case PLAYER2 -> PLAYER1;
            };
        }
    }

    public abstract class SetState {
        protected void checkValid() {
            if (state != this) throw new IllegalStateException("SetState is not valid");
        }

        @Nonnull
        public SmashSet getSmashSet() {
            checkValid();
            return SmashSet.this;
        }
    }

    public abstract class GameAssociatedState extends SetState {
        @Nonnull
        private final GameData game;

        public GameAssociatedState(@Nonnull GameData game) {
            this.game = game;
        }

        @Nonnull
        public GameData getGame() {
            return game;
        }
    }

    public abstract class DSRAffectedState extends GameAssociatedState {
        @Nonnull
        private final Player prevWinner;

        public DSRAffectedState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            super(game);
            this.prevWinner = prevWinner;
        }

        @Nonnull
        public Player getPrevWinner() {
            checkValid();
            return prevWinner;
        }

        @Nonnull
        public Player getPrevLoser() {
            checkValid();
            return prevWinner.invert();
        }

        @Nonnull
        public Set<Integer> getDSRIllegalStageIds() {
            checkValid();

            switch (ruleset.dsrMode()) {
                case NONE:
                    return Collections.emptySet();
                case MODIFIED_DSR:
                    // Find last stage the loser of the last game won on
                    return getPreviousLoserWinningStagesStream()
                            .reduce((first, second) -> second)
                            .stream()
                            .collect(Collectors.toSet());
                case GAME_RESTRICTED:
                    return games.stream()
                            .map(GameData::getStageId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                case WINNERS_VARIATION:
                    return getPreviousLoserWinningStagesStream()
                            .collect(Collectors.toSet());
                case STAGE_DISMISSAL_RULE:
                    Set<Integer> dsrIllegal = new HashSet<>();

                    // It's ok to access games.get(1) here
                    // A DSRAffectedState only happens after at least one game is completed
                    // We only go to games.size() - 1 because the current game guaranteed doesn't have a stage yet
                    for (int i = 1; i < games.size() - 1; i++) {
                        GameData prevGame = games.get(i - 1);
                        GameData game = games.get(i);

                        Player prevGameLoser = prevGame.getLoser();
                        // Only stages where the current previous loser counterpicked are relevant
                        if (prevGameLoser != getPrevLoser()) continue;

                        // Only counterpick stages are relevant
                        Integer stageId = game.getStageId();

                        // Every game before the current one (before games.size() - 1) will have a stage
                        //noinspection ConstantConditions
                        if (ruleset.counterpicks().stream().map(Stage::stageId).noneMatch(stageId::equals))
                            continue;

                        // Only stages where the current previous loser won are relevant
                        if (game.getWinner() != getPrevLoser()) continue;

                        dsrIllegal.add(stageId);
                    }

                    return dsrIllegal;
                default:
                    throw new IllegalStateException("Incomplete switch over DSR Mode");
            }
        }

        @Nonnull
        private Stream<Integer> getPreviousLoserWinningStagesStream() {
            return games.stream()
                    .filter(game -> game.getWinner() != null && game.getWinner() == getPrevLoser())
                    .map(GameData::getStageId);
        }
    }

    public class SetRPSState extends GameAssociatedState {
        public SetRPSState(@Nonnull GameData game) {
            super(game);
        }

        @Nonnull
        public synchronized SetStarterStrikingState completeRPS(@Nonnull Player rpsWinner, @Nonnull Player firstStriker) {
            checkValid();

            SmashSet.this.rpsWinner = rpsWinner;
            SmashSet.this.firstStageStriker = firstStriker;
            SetStarterStrikingState state = new SetStarterStrikingState(getGame());
            return switchState(state);
        }
    }

    public class SetStarterStrikingState extends GameAssociatedState {
        @Nonnull
        private Player currentStriker;

        private SetStarterStrikingState(@Nonnull GameData game) {
            super(game);

            Player firstStriker = getFirstStageStriker();
            if (firstStriker == null)
                throw new IllegalStateException("SetStarterStrikingState with firstStageStriker == null");
            currentStriker = firstStriker;
        }

        /**
         * @return Null if we stay in this stage
         * @throws IllegalStateException if too many or too few strikes are given
         */
        // TODO: Custom exceptions, keep as much info as possible in computer readable format
        @Nullable
        public synchronized OneOfTwo<SetDoubleBlindState, SetInGameState> strikeStages(@Nonnull Set<Integer> stageStrikingIds) {
            checkValid();

            // Verification checks
            int[] starterStrikePattern = ruleset.starterStrikePattern();
            int neededStrikeAmount = starterStrikePattern[stageStrikingIdHistory.size()];
            if (neededStrikeAmount != stageStrikingIds.size())
                throw new IllegalStateException(String.format("%d strikes were needed, %d given", neededStrikeAmount, stageStrikingIds.size()));
            if (stageStrikingIdHistory.stream().flatMap(Collection::stream).anyMatch(stageStrikingIds::contains))
                throw new IllegalStateException("A stage in stageStrikingIds was already struck");

            // Do the strike
            stageStrikingIdHistory.add(stageStrikingIds);
            if (stageStrikingIdHistory.size() == starterStrikePattern.length) {
                int starterAmount = ruleset.starters().size();
                // TODO: Better algorithm??
                int stageId = -1;
                for (int i = 0; i < starterAmount; i++) {
                    int finalI = i;
                    if (stageStrikingIdHistory.stream().flatMap(Collection::stream).noneMatch(struck -> finalI == struck)) {
                        stageId = ruleset.starters().get(i).stageId();
                        break;
                    }
                }

                if (stageId < 0)
                    throw new IllegalStateException("No stage left after striking, the ruleset validation might be broken");

                GameData game = getGame();

                game.setStageId(stageId);

                if (ruleset.blindPickBeforeStage()) {
                    SetInGameState state = new SetInGameState(game);
                    return OneOfTwo.ofU(switchState(state));
                } else {
                    SetDoubleBlindState state = new SetDoubleBlindState(game);
                    return OneOfTwo.ofT(switchState(state));
                }
            }

            currentStriker = currentStriker.invert();
            return null;
        }
    }

    public class SetDoubleBlindState extends GameAssociatedState {
        private SetDoubleBlindState(@Nonnull GameData game) {
            super(game);
        }

        @Nonnull
        public OneOfTwo<OneOfTwo<SetRPSState, SetStarterStrikingState>, SetInGameState> completeDoubleBlind(@Nonnull Character player1Char, @Nonnull Character player2Char) {
            checkValid();

            GameData game = getGame();

            game.setPlayer1Char(player1Char);
            game.setPlayer2Char(player2Char);
            if (ruleset.blindPickBeforeStage()) {
                if (doRPS) {
                    SetRPSState state = new SetRPSState(game);
                    return OneOfTwo.ofT(OneOfTwo.ofT(switchState(state)));
                } else {
                    SetStarterStrikingState state = new SetStarterStrikingState(game);
                    return OneOfTwo.ofT(OneOfTwo.ofU(switchState(state)));
                }
            } else {
                SetInGameState state = new SetInGameState(game);
                return OneOfTwo.ofU(switchState(state));
            }
        }
    }

    public class SetInGameState extends GameAssociatedState {
        private SetInGameState(@Nonnull GameData game) {
            super(game);
        }

        public synchronized void registerConflict(@Nonnull Conflict conflict) {
            checkValid();

            if (conflict.getResolution() != null)
                throw new IllegalStateException("At this point in time, the conflict may not be resolved");

            GameData game = getGame();

            if (game.getConflict() != null) throw new IllegalStateException("A conflict is already registered");

            game.setConflict(conflict);
        }

        @Nonnull
        public synchronized OneOfTwo<OneOfTwo<SetWinnerStageBanState, SetWinnerCharPickState>, SetCompletedState> resolveConflict(@Nonnull ConflictResolution resolution) {
            checkValid();

            Conflict conflict = getGame().getConflict();
            if (conflict == null) throw new IllegalStateException("No conflict was registered");

            conflict.setResolution(resolution);

            Player winner = conflict.isBothClaimedWin() ?
                    resolution.rightfulPlayer()
                    : resolution.wrongfulPlayer();

            return completeGame(winner);
        }

        @Nonnull
        public synchronized OneOfTwo<OneOfTwo<SetWinnerStageBanState, SetWinnerCharPickState>, SetCompletedState> completeGame(@Nonnull Player winner) {
            checkValid();

            getGame().setWinner(winner);
            if (games.stream().map(GameData::getWinner).filter(w -> w == winner).count() == firstToWhatScore) {
                SetCompletedState state = new SetCompletedState();
                return OneOfTwo.ofU(switchState(state));
            }

            GameData nextGame = new GameData(true);
            games.add(nextGame);

            if (ruleset.stageBeforeCharacter()) {
                SetWinnerStageBanState state = new SetWinnerStageBanState(nextGame, winner);
                return OneOfTwo.ofT(OneOfTwo.ofT(switchState(state)));
            } else {
                SetWinnerCharPickState state = new SetWinnerCharPickState(nextGame, winner);
                SmashSet.this.state = state;
                return OneOfTwo.ofT(OneOfTwo.ofU(switchState(state)));
            }
        }
    }

    public class SetWinnerCharPickState extends DSRAffectedState {
        private SetWinnerCharPickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            super(game, prevWinner);
        }

        @Nonnull
        public synchronized SetLoserCharCounterpickState pickCharacter(@Nonnull Character character) {
            checkValid();

            Player prevWinner = getPrevWinner();
            GameData game = getGame();

            switch (prevWinner) {
                case PLAYER1 -> game.setPlayer1Char(character);
                case PLAYER2 -> game.setPlayer2Char(character);
            }

            SetLoserCharCounterpickState state = new SetLoserCharCounterpickState(game, prevWinner);
            return switchState(state);
        }
    }

    public class SetLoserCharCounterpickState extends DSRAffectedState {
        private SetLoserCharCounterpickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            super(game, prevWinner);
        }

        @Nonnull
        public synchronized OneOfTwo<SetWinnerStageBanState, SetInGameState> pickCharacter(@Nonnull Character character) {
            checkValid();

            Player prevWinner = getPrevWinner();
            GameData game = getGame();

            switch (prevWinner.invert()) {
                case PLAYER1 -> game.setPlayer1Char(character);
                case PLAYER2 -> game.setPlayer2Char(character);
            }

            if (ruleset.stageBeforeCharacter()) {
                SetInGameState state = new SetInGameState(game);
                SmashSet.this.state = state;
                return OneOfTwo.ofU(switchState(state));
            } else {
                SetWinnerStageBanState state = new SetWinnerStageBanState(game, prevWinner);
                return OneOfTwo.ofT(switchState(state));
            }
        }
    }

    public class SetWinnerStageBanState extends DSRAffectedState {
        private SetWinnerStageBanState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            super(game, prevWinner);
        }

        @Nonnull
        public synchronized SetLoserStageCounterpickState banStages(@Nonnull Set<Integer> stageBanIds) {
            checkValid();

            if (stageBanIds.size() != ruleset.stageBans())
                throw new IllegalArgumentException(String.format("Wrong number of stage bans: %d given, %d required.", stageBanIds.size(), ruleset.stageBans()));

            GameData game = getGame();

            Set<Integer> alreadyBannedIds = game.getStageBanIds();
            if (alreadyBannedIds != null && stageBanIds.stream().anyMatch(alreadyBannedIds::contains))
                throw new IllegalStateException("Stage was banned twice");

            if (stageBanIds.stream().anyMatch(getDSRIllegalStageIds()::contains))
                throw new IllegalStateException("DSR illegal stage was banned");

            game.setStageBanIds(stageBanIds);

            SetLoserStageCounterpickState state = new SetLoserStageCounterpickState(game, getPrevWinner());
            return switchState(state);
        }
    }

    public class SetLoserStageCounterpickState extends DSRAffectedState {
        private SetLoserStageCounterpickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            super(game, prevWinner);
        }

        @Nonnull
        public synchronized OneOfTwo<SetWinnerCharPickState, SetInGameState> pickStage(int stageId) {
            checkValid();

            GameData game = getGame();

            // This will only be called once bans were done
            //noinspection ConstantConditions
            if (game.getStageBanIds().contains(stageId)) throw new IllegalStateException("StageId is banned");

            game.setStageId(stageId);

            if (ruleset.stageBeforeCharacter()) {
                SetWinnerCharPickState state = new SetWinnerCharPickState(game, getPrevWinner());
                return OneOfTwo.ofT(switchState(state));
            } else {
                SetInGameState state = new SetInGameState(game);
                return OneOfTwo.ofU(switchState(state));
            }
        }
    }

    public class SetCompletedState extends SetState {
        private SetCompletedState() {
        }
        // TODO: Maybe check validity in constructor??
    }

    public static class GameData {
        @Nullable
        Conflict conflict;
        @Nullable
        private Player winner;
        @Nullable
        private Character player1Char;
        @Nullable
        private Character player2Char;
        private final boolean useBans;
        /**
         * null if this is the first game in the set (see strikes) or bans have not taken place yet
         */
        @Nullable
        private Set<Integer> stageBanIds;
        @Nullable
        private Integer stageId;

        public GameData(boolean useBans) {
            this.useBans = useBans;
        }

        public void setConflict(@Nullable Conflict conflict) {
            this.conflict = conflict;
        }

        public void setWinner(@Nonnull Player winner) {
            this.winner = winner;
        }

        public void setPlayer1Char(@Nonnull Character player1Char) {
            this.player1Char = player1Char;
        }

        public void setPlayer2Char(@Nonnull Character player2Char) {
            this.player2Char = player2Char;
        }

        public void setStageId(int stageId) {
            this.stageId = stageId;
        }

        public void setStageBanIds(@Nonnull Set<Integer> stageBanIds) {
            if (!useBans) throw new IllegalStateException("Stage bans are not active in this game");
            this.stageBanIds = stageBanIds;
        }

        @Nullable
        public Conflict getConflict() {
            return conflict;
        }

        @Nullable
        public Player getWinner() {
            return winner;
        }

        @Nullable
        public Player getLoser() {
            return winner == null ? null : winner.invert();
        }

        @Nullable
        public Character getPlayer1Char() {
            return player1Char;
        }

        @Nullable
        public Character getPlayer2Char() {
            return player2Char;
        }

        /**
         * @return Null if this is the first game in the set (see strikes) or if bans haven't taken place yet
         */
        @Nullable
        public Set<Integer> getStageBanIds() {
            return stageBanIds;
        }

        @Nullable
        public Integer getStageId() {
            return stageId;
        }
    }

    public static class Conflict {
        private final boolean bothClaimedWin;
        @Nullable
        private ConflictResolution resolution;

        public Conflict(boolean bothClaimedWin) {
            this.bothClaimedWin = bothClaimedWin;
        }

        public void setResolution(@Nullable ConflictResolution resolution) {
            this.resolution = resolution;
        }

        public boolean isBothClaimedWin() {
            return bothClaimedWin;
        }

        @Nullable
        public ConflictResolution getResolution() {
            return resolution;
        }
    }

    public record ConflictResolution(@Nonnull Player wrongfulPlayer,
                                     @Nullable Long interveningMod) {
        @Nonnull
        public Player rightfulPlayer() {
            return wrongfulPlayer.invert();
        }
    }
}
