package com.github.gpluscb.toni.util.smash;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Stream;

public class Ruleset {
    private final long id;
    @Nonnull
    private final String name;
    @Nonnull
    private final List<Stage> starters;
    @Nonnull
    private final List<Stage> counterpicks;
    @Nonnull
    private final DSRMode dsrMode;
    private final int stageBans;
    private final int[] starterStrikePattern;
    private final boolean stageBeforeCharacter;

    public Ruleset(long id, @Nonnull String name, @Nonnull List<Stage> starters, @Nonnull List<Stage> counterpicks, @Nonnull DSRMode dsrMode, int stageBans, int[] starterStrikePattern, boolean stageBeforeCharacter) {
        this.id = id;
        this.name = name;
        this.starters = starters;
        this.counterpicks = counterpicks;
        this.dsrMode = dsrMode;
        this.stageBans = stageBans;
        this.starterStrikePattern = starterStrikePattern;
        this.stageBeforeCharacter = stageBeforeCharacter;

        // TODO: Validate
    }

    public long getId() {
        return id;
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

    public boolean getStageBeforeCharacter() {
        return stageBeforeCharacter;
    }

    /**
     * <a href=https://www.ssbwiki.com/Dave%27s_Stupid_Rule>SSBWiki for DSR</a>
     */
    public enum DSRMode {
        NONE,
        MODIFIED_DSR,
        WINNERS_VARIATION,
        STAGE_DISMISSAL_RULE,
    }
}
