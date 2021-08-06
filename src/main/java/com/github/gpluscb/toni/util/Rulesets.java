package com.github.gpluscb.toni.util;

import com.github.gpluscb.toni.util.smash.Ruleset;
import com.github.gpluscb.toni.util.smash.Stage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class Rulesets {
    @Nonnull
    private final List<Stage> stages;
    @Nonnull
    private final List<RawRuleset> rulesets;

    public Rulesets(@Nonnull List<Stage> stages, @Nonnull List<RawRuleset> rulesets) {
        this.stages = stages;
        this.rulesets = rulesets;
    }

    @SuppressWarnings("ConstantConditions")
    public void check() {
        if (stages == null) throw new IllegalStateException("stages may not be null");
        if (stages.contains(null)) throw new IllegalStateException("stages may not contain null elements");
        if (rulesets == null) throw new IllegalStateException("rulesets may not be null");
        if (rulesets.contains(null)) throw new IllegalStateException("rulesets may not contain null elements");
        rulesets.forEach(RawRuleset::check);
    }

    @Nonnull
    public List<Ruleset> toRulesetList() {
        return rulesets.stream().map(RawRuleset::toRuleset).collect(Collectors.toList());
    }

    @Nonnull
    public List<Stage> getStages() {
        return stages;
    }

    @Nonnull
    public List<RawRuleset> getRulesets() {
        return rulesets;
    }

    private class RawRuleset {
        private final int rulesetId;
        @Nonnull
        private final String name;
        @Nonnull
        private final List<Integer> starterIds;
        @Nonnull
        private final List<Integer> counterpickIds;
        @Nonnull
        private final Ruleset.DSRMode dsrMode;
        private final int stageBans;
        @Nonnull
        private final int[] starterStrikePattern;
        private final boolean stageBeforeCharacter;
        private final boolean blindPickBeforeStage;

        public RawRuleset(int rulesetId, @Nonnull String name, @Nonnull List<Integer> starterIds, @Nonnull List<Integer> counterpickIds, @Nonnull Ruleset.DSRMode dsrMode, int stageBans, int[] starterStrikePattern, boolean stageBeforeCharacter, boolean blindPickBeforeStage) {
            this.rulesetId = rulesetId;
            this.name = name;
            this.starterIds = starterIds;
            this.counterpickIds = counterpickIds;
            this.dsrMode = dsrMode;
            this.stageBans = stageBans;
            this.starterStrikePattern = starterStrikePattern;
            this.stageBeforeCharacter = stageBeforeCharacter;
            this.blindPickBeforeStage = blindPickBeforeStage;
        }

        @SuppressWarnings("ConstantConditions")
        public void check() {
            if (name == null) throw new IllegalStateException("name may not be null");
            if (starterIds == null) throw new IllegalStateException("starterIds may not be null");
            if (starterIds.contains(null)) throw new IllegalStateException("starterIds may not contain null elements");
            if (counterpickIds == null) throw new IllegalStateException("counterpickIds may not be null");
            if (counterpickIds.contains(null))
                throw new IllegalStateException("counterpickIds may not contain null elements");
            if (dsrMode == null) throw new IllegalStateException("dsrMode may not be null");
            if (starterStrikePattern == null) throw new IllegalStateException("starterStrikePattern may not be null");
        }

        @Nonnull
        public Ruleset toRuleset() {
            List<Stage> starters = starterIds.stream()
                    .map(this::stageFromId)
                    .collect(Collectors.toList());

            if (starters.contains(null)) throw new IllegalStateException("starterIds contains invalid id");

            List<Stage> counterpicks = counterpickIds.stream()
                    .map(this::stageFromId)
                    .collect(Collectors.toList());

            if (counterpicks.contains(null)) throw new IllegalStateException("counterpickIds contains invalid id");

            return new Ruleset(rulesetId, name, starters, counterpicks, dsrMode, stageBans, starterStrikePattern, stageBeforeCharacter, blindPickBeforeStage);
        }

        @Nullable
        private Stage stageFromId(int id) {
            return stages.stream()
                    .filter(stage -> stage.getStageId() == id)
                    .findFirst()
                    .orElse(null);
        }

        public long getRulesetId() {
            return rulesetId;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        public List<Integer> getStarterIds() {
            return starterIds;
        }

        @Nonnull
        public List<Integer> getCounterpickIds() {
            return counterpickIds;
        }

        @Nonnull
        public Ruleset.DSRMode getDsrMode() {
            return dsrMode;
        }

        public int getStageBans() {
            return stageBans;
        }

        public int[] getStarterStrikePattern() {
            return starterStrikePattern;
        }

        public boolean isStageBeforeCharacter() {
            return stageBeforeCharacter;
        }

        public boolean isBlindPickBeforeStage() {
            return blindPickBeforeStage;
        }
    }
}
