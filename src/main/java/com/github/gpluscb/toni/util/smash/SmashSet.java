package com.github.gpluscb.toni.util.smash;

import com.github.gpluscb.toni.util.OneOfTwo;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SmashSet {
    @Nonnull
    private final Ruleset ruleset;
    private final int firstToWhatScore;
    @Nullable
    private Player rpsWinner;
    @Nullable
    private Player firstStageStriker;
    @Nonnull
    private final List<Set<Integer>> stageStrikingIdxHistory;
    @Nullable
    private SetState state;
    @Nonnull
    private final List<GameData> games;

    public SmashSet(@Nonnull Ruleset ruleset, int firstToWhatScore) {
        this.ruleset = ruleset;
        this.firstToWhatScore = firstToWhatScore;
        firstStageStriker = null;
        stageStrikingIdxHistory = new ArrayList<>(ruleset.getStarterStrikePattern().length);
        state = null;
        games = new ArrayList<>();

        Integer maximumFirstToWhatScore = ruleset.getMaximumFirstToWhatScore();
        if (maximumFirstToWhatScore != null && firstToWhatScore > maximumFirstToWhatScore)
            throw new IllegalArgumentException("The DSR variation of the ruleset combined with this stage list does not allow for a set this long");
    }

    @Nonnull
    public synchronized SetRPSState startRPS() {
        if (state != null) throw new IllegalStateException("Set already started");
        SetRPSState state = new SetRPSState();
        this.state = state;
        return state;
    }

    public int getFirstToWhatScore() {
        return firstToWhatScore;
    }

    @Nonnull
    public List<GameData> getGames() {
        return games;
    }

    @Nonnull
    public List<Set<Integer>> getStageStrikingIdxHistory() {
        return stageStrikingIdxHistory;
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
            switch (this) {
                case PLAYER1:
                    return PLAYER2;
                case PLAYER2:
                    return PLAYER1;
                default:
                    throw new IllegalStateException("Player doesn't exist");
            }
        }
    }

    public abstract class SetState {
        protected void checkValid() {
            if (state != this) throw new IllegalStateException("SetState is not valid");
        }

        @Nonnull
        public SmashSet getSmashSet() {
            return SmashSet.this;
        }
    }

    public class SetRPSState extends SetState {
        @Nonnull
        private final GameData game;

        public SetRPSState() {
            game = new GameData(false);
            games.add(game);
        }

        public SetRPSState(@Nonnull GameData game) {
            this.game = game;
            games.add(game);
        }

        @Nonnull
        public synchronized SetStarterStrikingState completeRPS(@Nonnull Player rpsWinner, @Nonnull Player firstStriker) {
            checkValid();
            SmashSet.this.rpsWinner = rpsWinner;
            SmashSet.this.firstStageStriker = firstStriker;
            SetStarterStrikingState state = new SetStarterStrikingState(firstStriker, game);
            return switchState(state);
        }
    }

    public class SetStarterStrikingState extends SetState {
        @Nonnull
        private final GameData game;

        @Nonnull
        private Player currentStriker;

        public SetStarterStrikingState(@Nonnull Player firstStriker, @Nonnull GameData game) {
            currentStriker = firstStriker;
            this.game = game;
        }

        /**
         * @return Null if we stay in this stage
         * @throws IllegalStateException if too many or too few strikes are given
         */
        // TODO: Custom exceptions, keep as much info as possible in computer readable format
        @Nullable
        public synchronized OneOfTwo<SetDoubleBlindState, SetInGameState> strikeStages(@Nonnull Set<Integer> stageStrikingIndizes) {
            checkValid();
            // Verification checks
            int[] starterStrikePattern = ruleset.getStarterStrikePattern();
            int neededStrikeAmount = starterStrikePattern[stageStrikingIdxHistory.size()];
            if (neededStrikeAmount != stageStrikingIndizes.size())
                throw new IllegalStateException(String.format("%d strikes were needed, %d given", neededStrikeAmount, stageStrikingIndizes.size()));
            if (stageStrikingIdxHistory.stream().flatMap(Collection::stream).anyMatch(stageStrikingIndizes::contains))
                throw new IllegalStateException("A stage in stageStrikingIndizes was already struck");

            // Do the strike
            stageStrikingIdxHistory.add(stageStrikingIndizes);
            if (stageStrikingIdxHistory.size() == starterStrikePattern.length) {
                int starterAmount = ruleset.getStarters().size();
                // TODO: Better algorithm??
                int stageIdx = -1;
                for (int i = 0; i < starterAmount; i++) {
                    int finalI = i;
                    if (stageStrikingIdxHistory.stream().flatMap(Collection::stream).noneMatch(struck -> finalI == struck)) {
                        stageIdx = i;
                        break;
                    }
                }

                if (stageIdx < 0)
                    throw new IllegalStateException("No stage left after striking, the ruleset validation might be broken");

                if (ruleset.isBlindPickBeforeStage()) {
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

    public class SetDoubleBlindState extends SetState {
        @Nonnull
        private final GameData game;

        public SetDoubleBlindState(@Nonnull GameData game) {
            this.game = game;
        }

        @Nonnull
        public OneOfTwo<SetRPSState, SetInGameState> completeDoubleBlind(@Nonnull Character player1Char, @Nonnull Character player2Char) {
            checkValid();
            game.setPlayer1Char(player1Char);
            game.setPlayer2Char(player2Char);
            if (ruleset.isBlindPickBeforeStage()) {
                SetRPSState state = new SetRPSState(game);
                return OneOfTwo.ofT(switchState(state));
            } else {
                SetInGameState state = new SetInGameState(game);
                return OneOfTwo.ofU(switchState(state));
            }
        }
    }

    public class SetInGameState extends SetState {
        @Nonnull
        private final GameData game;

        public SetInGameState(@Nonnull GameData game) {
            this.game = game;
        }

        @Nonnull
        public synchronized OneOfTwo<OneOfTwo<SetWinnerStageBanState, SetWinnerCharPickState>, SetCompletedState> completeGame(@Nonnull Player winner) {
            checkValid();
            game.setWinner(winner);
            if (games.stream().map(GameData::getWinner).filter(w -> w == winner).count() == firstToWhatScore) {
                SetCompletedState state = new SetCompletedState();
                return OneOfTwo.ofU(switchState(state));
            }

            GameData nextGame = new GameData(true);
            games.add(nextGame);

            if (ruleset.isStageBeforeCharacter()) {
                SetWinnerStageBanState state = new SetWinnerStageBanState(nextGame, winner);
                return OneOfTwo.ofT(OneOfTwo.ofT(switchState(state)));
            } else {
                SetWinnerCharPickState state = new SetWinnerCharPickState(nextGame, winner);
                SmashSet.this.state = state;
                return OneOfTwo.ofT(OneOfTwo.ofU(switchState(state)));
            }
        }
    }

    public class SetWinnerCharPickState extends SetState {
        @Nonnull
        private final GameData game;
        @Nonnull
        private final Player prevWinner;

        public SetWinnerCharPickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            this.game = game;
            this.prevWinner = prevWinner;
        }

        @Nonnull
        public synchronized SetLoserCharCounterpickState pickCharacter(@Nonnull Character character) {
            checkValid();
            switch (prevWinner) {
                case PLAYER1:
                    game.setPlayer1Char(character);
                    break;
                case PLAYER2:
                    game.setPlayer2Char(character);
                    break;
            }

            SetLoserCharCounterpickState state = new SetLoserCharCounterpickState(game, prevWinner);
            return switchState(state);
        }
    }

    public class SetLoserCharCounterpickState extends SetState {
        @Nonnull
        private final GameData game;
        @Nonnull
        private final Player prevWinner;

        public SetLoserCharCounterpickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            this.game = game;
            this.prevWinner = prevWinner;
        }

        @Nonnull
        public synchronized OneOfTwo<SetWinnerStageBanState, SetInGameState> pickCharacter(@Nonnull Character character) {
            checkValid();
            switch (prevWinner.invert()) {
                case PLAYER1:
                    game.setPlayer1Char(character);
                    break;
                case PLAYER2:
                    game.setPlayer2Char(character);
                    break;
            }

            if (ruleset.isStageBeforeCharacter()) {
                SetInGameState state = new SetInGameState(game);
                SmashSet.this.state = state;
                return OneOfTwo.ofU(switchState(state));
            } else {
                SetWinnerStageBanState state = new SetWinnerStageBanState(game, prevWinner);
                return OneOfTwo.ofT(switchState(state));
            }
        }
    }

    public class SetWinnerStageBanState extends SetState {
        @Nonnull
        private final GameData game;
        @Nonnull
        private final Player prevWinner;

        public SetWinnerStageBanState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            this.game = game;
            this.prevWinner = prevWinner;
        }

        @Nonnull
        public synchronized SetLoserStageCounterpickState banStages(@Nonnull Set<Integer> stageBanIndices) {
            checkValid();
            // TODO: Verification that these stages are even legal to ban
            if (stageBanIndices.size() != ruleset.getStageBans())
                throw new IllegalArgumentException(String.format("Wrong number of stage bans: %d given, %d required.", stageBanIndices.size(), ruleset.getStageBans()));

            game.setStageBanIndices(stageBanIndices);

            SetLoserStageCounterpickState state = new SetLoserStageCounterpickState(game, prevWinner);
            return switchState(state);
        }
    }

    public class SetLoserStageCounterpickState extends SetState {
        @Nonnull
        private final GameData game;
        @Nonnull
        private final Player prevWinner;

        public SetLoserStageCounterpickState(@Nonnull GameData game, @Nonnull Player prevWinner) {
            this.game = game;
            this.prevWinner = prevWinner;
        }

        @Nonnull
        public synchronized OneOfTwo<SetWinnerCharPickState, SetInGameState> pickStage(int stageIdx) {
            checkValid();
            game.setStageIdx(stageIdx);

            if (ruleset.isStageBeforeCharacter()) {
                SetWinnerCharPickState state = new SetWinnerCharPickState(game, prevWinner);
                return OneOfTwo.ofT(switchState(state));
            } else {
                SetInGameState state = new SetInGameState(game);
                return OneOfTwo.ofU(switchState(state));
            }
        }
    }

    public class SetCompletedState extends SetState {
        // TODO: Maybe check validity in constructor??
    }

    public static class GameData {
        // TODO: save conflicts when reporting?
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
        private Set<Integer> stageBanIndices;
        @Nullable
        private Integer stageIdx;

        public GameData(boolean useBans) {
            this.useBans = useBans;
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

        public void setStageIdx(int stageIdx) {
            this.stageIdx = stageIdx;
        }

        public void setStageBanIndices(@Nonnull Set<Integer> stageBanIndices) {
            if (!useBans) throw new IllegalStateException("Stage bans are not active in this game");
            this.stageBanIndices = stageBanIndices;
        }

        @Nullable
        public Player getWinner() {
            return winner;
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
         * @return Null if this is the first game in the set (see strikes)
         */
        @Nullable
        public Set<Integer> getStageBanIndices() {
            return stageBanIndices;
        }

        @Nullable
        public Integer getStageIdx() {
            return stageIdx;
        }
    }
}
