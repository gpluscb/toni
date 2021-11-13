package com.github.gpluscb.toni.ultimateframedata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record CharacterData(@Nonnull String ufdUrl, @Nonnull String name,
                            @Nonnull List<MoveSection> moveSections,
                            @Nonnull com.github.gpluscb.toni.ultimateframedata.CharacterData.MiscData miscData) {
    public record MoveSection(@Nonnull String sectionName, @Nonnull String htmlId,
                              @Nonnull List<MoveData> moves) {
    }

    public record MoveData(
            @Nonnull List<HitboxData> hitboxes,
            @Nullable String moveName, @Nullable String startup, @Nullable String totalFrames,
            @Nullable String landingLag, @Nullable String notes, @Nullable String baseDamage,
            @Nullable String shieldLag, @Nullable String shieldStun,
            @Nullable String whichHitbox, @Nullable String advantage,
            @Nullable String activeFrames) {
    }

    public record HitboxData(@Nullable String name, @Nonnull String url) {
    }

    public record MiscData(@Nullable StatsData stats,
                           @Nonnull List<MoveData> moves,
                           @Nonnull String htmlId) {
    }

    public record StatsData(@Nullable String weight, @Nullable String gravity,
                            @Nullable String walkSpeed, @Nullable String runSpeed,
                            @Nullable String initialDash, @Nullable String airSpeed,
                            @Nullable String totalAirAcceleration, @Nullable String shFhShffFhffFrames,
                            @Nullable String fallSpeedFastFallSpeed,
                            @Nonnull List<String> oosOptions, @Nullable String shieldGrab,
                            @Nullable String shieldDrop, @Nullable String jumpSquat) {
    }
}
