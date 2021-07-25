package com.github.gpluscb.toni.util.smash;

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
    private Player firstStageStriker;
    @Nonnull
    private final List<Set<Integer>> stageStrikingIdxHistory;
    @Nonnull
    private SetState state;
    @Nonnull
    private final List<Player> gameResults;
    @Nonnull
    private final List<Set<Integer>> stageBanIdxHistory;
    @Nonnull
    private final List<Integer> stageIdxHistory;

    public SmashSet(@Nonnull Ruleset ruleset, int firstToWhatScore) {
        this.ruleset = ruleset;
        this.firstToWhatScore = firstToWhatScore;
        firstStageStriker = null;
        stageStrikingIdxHistory = new ArrayList<>(ruleset.getStarterStrikePattern().length);
        state = new SetRPSState();
        gameResults = new ArrayList<>();
        stageBanIdxHistory = new ArrayList<>();
        stageIdxHistory = new ArrayList<>();

        Integer maximumFirstToWhatScore = ruleset.getMaximumFirstToWhatScore();
        if (maximumFirstToWhatScore != null && firstToWhatScore > maximumFirstToWhatScore)
            throw new IllegalArgumentException("The DSR variation of the ruleset combined with this stage list does not allow for a set this long");
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

    public abstract static class SetState {
        private boolean invalid = false;

        protected void checkValid() {
            if (invalid) throw new IllegalStateException("SetState is not valid");
        }

        protected void invalidate() {
            invalid = true;
        }

        protected void checkValidAndInvalidate() {
            checkValid();
            invalidate();
        }
    }

    public class SetRPSState extends SetState {
        @Nonnull
        public synchronized SetStarterStrikingState completeRPS(@Nonnull Player firstStriker) {
            checkValidAndInvalidate();
            return new SetStarterStrikingState(firstStriker);
        }
    }

    public class SetStarterStrikingState extends SetState {
        @Nonnull
        private Player currentStriker;

        public SetStarterStrikingState(@Nonnull Player firstStriker) {
            currentStriker = firstStriker;
        }

        /**
         * @return Null if we stay in this stage
         * @throws IllegalStateException if too many or too few strikes are given
         */
        // TODO: Custom exceptions, keep as much info as possible in computer readable format
        @Nullable
        public synchronized SetInGameState strikeStages(@Nonnull Set<Integer> stageStrikingIndizes) {
            checkValid();
            // Sanity checks
            int[] starterStrikePattern = ruleset.getStarterStrikePattern();
            int neededStrikeAmount = starterStrikePattern[stageStrikingIdxHistory.size()];
            if (neededStrikeAmount != stageStrikingIndizes.size())
                throw new IllegalStateException(String.format("%d strikes were needed, %d given", neededStrikeAmount, stageStrikingIndizes.size()));
            if (stageStrikingIdxHistory.stream().flatMap(Collection::stream).anyMatch(stageStrikingIndizes::contains))
                throw new IllegalStateException("One stage was already struck");

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
                stageIdxHistory.add(stageIdx);

                invalidate();
                return new SetInGameState();
            }

            currentStriker = currentStriker.invert();
            return null;
        }
    }

    // TODO
    public class SetInGameState extends SetState {

    }

    public class SetWinnerCharPickState extends SetState {

    }

    public class SetLoserCharCounterpickState extends SetState {

    }

    public class SetWinnerStageBanState extends SetState {

    }

    public class SetLoserStageCounterpickState extends SetState {

    }

    public class SetCompletedState extends SetState {

    }
}
