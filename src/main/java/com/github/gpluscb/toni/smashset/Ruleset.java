package com.github.gpluscb.toni.smashset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// TODO: L4st ruleset requires 3 bans for BO3, 2 for BO5... How tf do we model this????
public record Ruleset(int rulesetId, @Nonnull String name, @Nonnull String shortDescription, @Nonnull String url,
                      @Nonnull List<Stage> starters,
                      @Nonnull List<Stage> counterpicks,
                      @Nonnull com.github.gpluscb.toni.smashset.Ruleset.DSRMode dsrMode, int stageBans,
                      int[] starterStrikePattern, boolean stageBeforeCharacter, boolean blindPickBeforeStage) {
    public Ruleset(int rulesetId, @Nonnull String name, @Nonnull String shortDescription, @Nonnull String url, @Nonnull List<Stage> starters, @Nonnull List<Stage> counterpicks, @Nonnull DSRMode dsrMode, int stageBans, int[] starterStrikePattern, boolean stageBeforeCharacter, boolean blindPickBeforeStage) {
        this.rulesetId = rulesetId;
        this.name = name;
        this.shortDescription = shortDescription;
        this.url = url;
        this.starters = starters;
        this.counterpicks = counterpicks;
        this.dsrMode = dsrMode;
        this.stageBans = stageBans;
        this.starterStrikePattern = starterStrikePattern;
        this.stageBeforeCharacter = stageBeforeCharacter;
        this.blindPickBeforeStage = blindPickBeforeStage;

        validate();
    }

    @SuppressWarnings("ConstantConditions")
    public void validate() {
        if (name == null) throw new IllegalStateException("Name may not be null");
        if (shortDescription == null) throw new IllegalStateException("ShortDescription may not be null");
        if (url == null) throw new IllegalStateException("Url may not be null");
        if (starters == null) throw new IllegalStateException("Name may not be null");
        if (counterpicks == null) throw new IllegalStateException("Name may not be null");
        if (dsrMode == null) throw new IllegalStateException("Name may not be null");

        int startersSize = starters.size();
        int stagesSize = startersSize + counterpicks.size();

        if (startersSize == 0) throw new IllegalArgumentException("There must be at least one starter stage");
        if (stageBans >= stagesSize) throw new IllegalArgumentException("There must be fewer stage bans than stages");
        if (Arrays.stream(starterStrikePattern).sum() != startersSize - 1)
            throw new IllegalArgumentException("The starter strike pattern must leave exactly one stage unstruck");
        if (Arrays.stream(starterStrikePattern).anyMatch(strikes -> strikes <= 0))
            throw new IllegalArgumentException("The starter strike pattern can only contain strictly positive numbers");
        if (dsrMode == DSRMode.MODIFIED_DSR && stagesSize - stageBans < 2)
            throw new IllegalArgumentException("Modified DSR requires that after stage bans, at least 2 stages must be left");
        Integer maximumFirstToWhatScore = getMaximumFirstToWhatScore();
        if (maximumFirstToWhatScore != null && maximumFirstToWhatScore == 0)
            throw new IllegalArgumentException("This ruleset with the selected DSR mode would not allow for any games to be played");
    }

    @Nonnull
    public Stream<Stage> getStagesStream() {
        return Stream.concat(starters.stream(), counterpicks.stream());
    }

    /**
     * Depends on the dsr setting, how many games can be played
     * Null if there is no limit
     */
    @Nullable
    public Integer getMaximumFirstToWhatScore() {
        int startersSize = starters.size();
        int counterpicksSize = counterpicks.size();
        int stagesSize = startersSize + counterpicksSize;

        return switch (dsrMode) {
            case NONE, MODIFIED_DSR ->
                    // For MODIFIED_DSR this is with some conditions, see the validate section
                    null;
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
                int maximumBestOfWhat = stagesSize - stageBans;
                if (maximumBestOfWhat % 2 == 0) maximumBestOfWhat--;
                yield (maximumBestOfWhat + 1) / 2;
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
                int maximumBestOfWhat = 2 * (stagesSize - stageBans) - 1;
                yield (maximumBestOfWhat + 1) / 2;
            }
            case STAGE_DISMISSAL_RULE -> {
                // TODO: Revisit this if I ever implement gentlemans clause
                // You can not play on a *counterpick* stage if you *counterpicked* it and won
                // So if we have more starters than bans, a *starter* stage will always be open
                if (startersSize > stageBans) yield null;
                // If that isn't the case, in the worst case scenario all starters are banned
                // So there may be some bans left for counterpicks
                int cpBans = stageBans - startersSize;
                // Now the limiting factor is really how many more counterpick stages than cpBans we have
                // Worst case scenario: First game was on a starter (obv), after that we only play on counterpicks
                // Both players always win on their counterpick
                // Score is x-x, Player 1 won on the starter and on x-1 of their own counterpicks
                // Player 2 won on x of their own counterpicks
                // So all in all we have at max x-1+cbBans counterpicks banned, plus all starters banned
                // Meaning we have counterpicksSize - x - cpBans + 1 stages left
                // So x+1 will be maximumBestOfWhat
                // At the end the stages left shall be 1
                // counterpicksSize - x - cpBans + 1 = 1
                // <=> x + 1 = maximumBestOfWhat = counterpicksSize - cpBans + 1
                int maximumBestOfWhat = counterpicksSize - cpBans + 1;
                if (maximumBestOfWhat % 2 == 0) maximumBestOfWhat--;
                yield (maximumBestOfWhat + 1) / 2;
            }
        };
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

        @Nullable
        public String getSsbwikiUrl() {
            return switch (this) {
                case NONE -> null;
                case MODIFIED_DSR -> "https://www.ssbwiki.com/Dave%27s_Stupid_Rule#Original_Ruling";
                case GAME_RESTRICTED -> "https://www.ssbwiki.com/Dave%27s_Stupid_Rule#Game-Restricted_Variation";
                case WINNERS_VARIATION -> "https://www.ssbwiki.com/Dave%27s_Stupid_Rule#Winner%27s_Variation";
                case STAGE_DISMISSAL_RULE -> "https://www.ssbwiki.com/Dave%27s_Stupid_Rule#%22Stage_Dismissal_Rule%22";
            };
        }

        @Nonnull
        public static DSRMode fromId(int id) {
            return DSRMode.values()[id];
        }
    }
}
