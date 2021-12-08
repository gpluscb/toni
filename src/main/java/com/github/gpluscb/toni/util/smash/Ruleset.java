package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// TODO: Somehow do RPS required or have that as a server wide setting? Alternative is random choice
// TODO: Short description?
public class Ruleset {
    private final int rulesetId;
    @Nonnull
    private final String name;
    @Nonnull
    private final List<Stage> starters;
    @Nonnull
    private final List<Stage> counterpicks;
    @Nonnull
    private final DSRMode dsrMode;
    /**
     * Depends on the dsr setting, how many games can be played
     * Null if there is no limit
     */
    @Nullable
    private final Integer maximumFirstToWhatScore;
    private final int stageBans;
    private final int[] starterStrikePattern;
    private final boolean stageBeforeCharacter;
    private final boolean blindPickBeforeStage;

    public Ruleset(int rulesetId, @Nonnull String name, @Nonnull List<Stage> starters, @Nonnull List<Stage> counterpicks, @Nonnull DSRMode dsrMode, int stageBans, int[] starterStrikePattern, boolean stageBeforeCharacter, boolean blindPickBeforeStage) {
        this.rulesetId = rulesetId;
        this.name = name;
        this.starters = starters;
        this.counterpicks = counterpicks;
        this.dsrMode = dsrMode;
        this.stageBans = stageBans;
        this.starterStrikePattern = starterStrikePattern;
        this.stageBeforeCharacter = stageBeforeCharacter;
        this.blindPickBeforeStage = blindPickBeforeStage;

        int stagesSize = starters.size() + counterpicks.size();
        int maximumBestOfWhat;
        switch (dsrMode) {
            case NONE, MODIFIED_DSR ->
                    // For MODIFIED_DSR this is with some conditions, see the validate section
                    maximumFirstToWhatScore = null;
            case GAME_RESTRICTED -> {
                // Worst case scenario: we played on x stages already, and stageBans *different* stages are banned
                // So we'll have (x + stageBans) *illegal* stages
                // This means we'll have (stagesSize - (x + stageBans)) = (stagesSize - x - stageBans) *legal* stages
                // We have to have at least one legal stage left before the last game
                // 1 = stagesSize - x - stageBans <=> x = stagesSize - stageBans - 1
                // After that we can play that one last game
                // Therefore: maximumBestOfWhat = (x + 1) = (stagesSize - stageBans) if that is odd,
                // (stagesSize - stageBans - 1) otherwise
                // and: maximumFirstToWhatScore = (maximumBestOfWhat + 1) / 2
                maximumBestOfWhat = stagesSize - stageBans;
                if (maximumBestOfWhat % 2 == 0) maximumBestOfWhat--;
                maximumFirstToWhatScore = (maximumBestOfWhat + 1) / 2;
            }
            case WINNERS_VARIATION -> {
                // Worst case scenario: both player 1 and player 2 have already won x games (meaning we played 2x games), and now stageBans *different* stages are banned
                // So we'll have (x + stageBans) *illegal* stages
                // This means we'll have (stagesSize - (x + stageBans)) = (stagesSize - x - stageBans) *legal* stages
                // We have to have at least one legal stage left before the last game
                // 1 = stagesSize - x - stageBans <=> x = stagesSize - stageBans - 1
                // <=> 2x = 2 (stagesSize - sageBans - 1)
                // After that we can play that one last game
                // Therefore: maximumBestOfWhat = (2x + 1) = (2 (stagesSize - stageBans - 1) + 1)
                // = (2 (stagesSize - stageBans) - 1), which is always odd
                // and: maximumFirstToWhatScore = (maximumBestOfWhat + 1) / 2
                // Note: We could have one more game in theory iff the previous loser wins, but that violates
                // the condition that maximumBestOfWhat must be odd
                maximumBestOfWhat = 2 * (stagesSize - stageBans) - 1;
                maximumFirstToWhatScore = (maximumBestOfWhat + 1) / 2;
            }
            case STAGE_DISMISSAL_RULE ->
                    // TODO: Revisit this if I ever implement gentlemans clause
                    // You can not play on a *counterpick* stage if you *counterpicked* it and  won
                    // So the limiting factor is really how many counterpick stages we have
                    // Worst case scenario: First game was on a starter (obv), after that we only play on counterpicks
                    // Both players always win on their counterpick
                    // Score is x-x, Player 1 won on the starter and on x-1 of their own counterpicks
                    // Player 2 won on x of their own counterpicks
                    // This implies something idekk
                    // TODO: AAAAAAAAAA maths
                    maximumFirstToWhatScore = stagesSize;
            default -> throw new IllegalStateException("Incomplete switch over DSR modes");
        }

        // Validate
        int startersSize = starters.size();
        if (startersSize == 0) throw new IllegalArgumentException("There must be at least one starter stage");
        if (stageBans >= stagesSize) throw new IllegalArgumentException("There must be fewer stage bans than stages");
        if (Arrays.stream(starterStrikePattern).sum() != startersSize - 1)
            throw new IllegalArgumentException("The starter strike pattern must leave exactly one stage unstruck");
        if (Arrays.stream(starterStrikePattern).anyMatch(strikes -> strikes <= 0))
            throw new IllegalArgumentException("The starter strike pattern can only contain strictly positive numbers");
        if (dsrMode == DSRMode.MODIFIED_DSR && stagesSize - stageBans < 2)
            throw new IllegalArgumentException("Modified DSR requires that after stage bans, at least 2 stages must be left");
    }

    public int getRulesetId() {
        return rulesetId;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public List<Stage> getStarters() {
        return starters;
    }

    @Nonnull
    public List<Stage> getCounterpicks() {
        return counterpicks;
    }

    @Nonnull
    public Stream<Stage> getStagesStream() {
        return Stream.concat(starters.stream(), counterpicks.stream());
    }

    @Nonnull
    public Stage getStageAtIdx(int idx) {
        if (idx < starters.size()) return starters.get(idx);
        else return counterpicks.get(idx - starters.size());
    }

    @Nonnull
    public DSRMode getDsrMode() {
        return dsrMode;
    }

    public int getStageBans() {
        return stageBans;
    }

    public int[] getStarterStrikePattern() {
        return starterStrikePattern;
    }

    @Nullable
    public Integer getMaximumFirstToWhatScore() {
        return maximumFirstToWhatScore;
    }

    public boolean isStageBeforeCharacter() {
        return stageBeforeCharacter;
    }

    public boolean isBlindPickBeforeStage() {
        return blindPickBeforeStage;
    }

    /**
     * <a href=https://www.ssbwiki.com/Dave%27s_Stupid_Rule>SSBWiki for DSR</a>
     */
    public enum DSRMode {
        NONE,
        MODIFIED_DSR,
        GAME_RESTRICTED,
        WINNERS_VARIATION,
        STAGE_DISMISSAL_RULE;

        @Nonnull
        public String displayName() {
            return switch (this) {
                case NONE -> "No DSR";
                case MODIFIED_DSR -> "Modified DSR";
                case GAME_RESTRICTED -> "Game Restricted DSR";
                case WINNERS_VARIATION -> "Winners Variation";
                case STAGE_DISMISSAL_RULE -> "Stage Dismissal Rule";
            };
        }

        @Nonnull
        public static DSRMode fromId(int id) {
            return DSRMode.values()[id];
        }
    }
}
