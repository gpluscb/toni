package com.github.gpluscb.toni.smashset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record Rulesets(@Nonnull List<Stage> stages,
                       @Nonnull List<RawRuleset> rulesets) {
    @SuppressWarnings("ConstantConditions")
    public void check() {
        if (stages == null) throw new IllegalStateException("stages may not be null");
        if (stages.contains(null)) throw new IllegalStateException("stages may not contain null elements");
        if (rulesets == null) throw new IllegalStateException("rulesets may not be null");
        if (rulesets.contains(null)) throw new IllegalStateException("rulesets may not contain null elements");
        stages.forEach(Stage::check);
        rulesets.forEach(RawRuleset::check);
    }

    @Nonnull
    public List<Ruleset> toRulesetList() {
        return rulesets.stream().map(raw -> raw.toRuleset(stages)).toList();
    }

    private record RawRuleset(int rulesetId, @Nonnull String name, @Nonnull String shortDescription, @Nonnull String url,
                              @Nonnull List<Integer> starterIds,
                              @Nonnull List<Integer> counterpickIds,
                              @Nonnull Ruleset.DSRMode dsrMode, int stageBans,
                              @Nonnull int[] starterStrikePattern, boolean stageBeforeCharacter,
                              boolean blindPickBeforeStage) {
        @SuppressWarnings("ConstantConditions")
        public void check() {
            if (name == null) throw new IllegalStateException("name may not be null");
            if (shortDescription == null) throw new IllegalStateException("shortDescription may not be null");
            if (url == null) throw new IllegalStateException("url may not be null");
            if (starterIds == null) throw new IllegalStateException("starterIds may not be null");
            if (starterIds.contains(null)) throw new IllegalStateException("starterIds may not contain null elements");
            if (counterpickIds == null) throw new IllegalStateException("counterpickIds may not be null");
            if (counterpickIds.contains(null))
                throw new IllegalStateException("counterpickIds may not contain null elements");
            if (dsrMode == null) throw new IllegalStateException("dsrMode may not be null");
            if (starterStrikePattern == null) throw new IllegalStateException("starterStrikePattern may not be null");
        }

        @Nonnull
        public Ruleset toRuleset(@Nonnull List<Stage> stages) {
            List<Stage> starters = starterIds.stream()
                    .map(id -> stageFromId(stages, id))
                    .toList();

            if (starters.contains(null)) throw new IllegalStateException("starterIds contains invalid id");

            List<Stage> counterpicks = counterpickIds.stream()
                    .map(id -> stageFromId(stages, id))
                    .toList();

            if (counterpicks.contains(null)) throw new IllegalStateException("counterpickIds contains invalid id");

            return new Ruleset(rulesetId, name, shortDescription, url, starters, counterpicks, dsrMode, stageBans, starterStrikePattern, stageBeforeCharacter, blindPickBeforeStage);
        }

        @Nullable
        private Stage stageFromId(@Nonnull List<Stage> stages, int id) {
            return stages.stream()
                    .filter(stage -> stage.stageId() == id)
                    .findFirst()
                    .orElse(null);
        }
    }
}
