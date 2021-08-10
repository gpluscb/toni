package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// TODO: Somehow do RPS required or have that as a server wide setting? Alternative is random choice
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
        switch (dsrMode) {
            case WINNERS_VARIATION:
                maximumFirstToWhatScore = stagesSize;
                break;
            case GAME_RESTRICTED:
                int maximumBestOf = stagesSize % 2 == 0 ?
                        stagesSize - 1
                        : stagesSize;
                maximumFirstToWhatScore = (maximumBestOf + 1) / 2;
                break;
            case STAGE_DISMISSAL_RULE:
                // TODO: This seems wrong I think
                maximumFirstToWhatScore = stagesSize;
                break;
            default:
                maximumFirstToWhatScore = null;
                break;
        }

        // Validate
        int startersSize = starters.size();
        if (startersSize == 0) throw new IllegalArgumentException("There must be at least one starter stage");
        if (stageBans >= stagesSize) throw new IllegalArgumentException("There must be fewer stage bans than stages");
        if (Arrays.stream(starterStrikePattern).sum() != startersSize - 1)
            throw new IllegalArgumentException("The starter strike pattern must leave exactly one stage unstruck");
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
        public static DSRMode fromId(int id) {
            return DSRMode.values()[id];
        }
    }
}
