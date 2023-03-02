package com.github.gpluscb.toni.ultimateframedata;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

// TODO: the @SerializedName annotations might not be needed after gson allows for records natively
public record CharacterData(@Nonnull @SerializedName("ufd_url") String ufdUrl, @Nonnull String name,
                            @Nonnull @SerializedName("move_sections") List<MoveSection> moveSections,
                            @Nonnull @SerializedName("misc_data") MiscData miscData) {
    public record MoveSection(@Nonnull @SerializedName("section_name") String sectionName,
                              @SerializedName("html_id") @Nonnull String htmlId,
                              @Nonnull List<MoveData> moves) {
    }

    public record MoveData(
            @Nonnull List<HitboxData> hitboxes,
            @Nullable @SerializedName("move_name") String moveName, @Nullable String startup,
            @Nullable @SerializedName("total_frames") String totalFrames,
            @Nullable @SerializedName("landing_lag") String landingLag, @Nullable String notes,
            @SerializedName("base_damage") @Nullable String baseDamage,
            @Nullable @SerializedName("shield_lag") String shieldLag,
            @SerializedName("shield_stun") @Nullable String shieldStun,
            @Nullable @SerializedName("which_hitbox") String whichHitbox, @Nullable String advantage,
            @Nullable @SerializedName("active_frames") String activeFrames,
            @Nullable @SerializedName("hops_autocancel") String hopsAutocancel,
            @Nullable @SerializedName("hops_actionable") String hopsActionable,
            @Nullable String endlag) {
    }

    public record HitboxData(@Nullable String name, @Nonnull String url) {
    }

    public record MiscData(@Nullable StatsData stats,
                           @Nonnull List<MoveData> moves,
                           @Nonnull @SerializedName("html_id") String htmlId) {
    }

    public record StatsData(@Nullable String weight, @Nullable String gravity,
                            @Nullable @SerializedName("walk_speed") String walkSpeed,
                            @SerializedName("run_speed") @Nullable String runSpeed,
                            @Nullable @SerializedName("initial_dash") String initialDash,
                            @Nullable @SerializedName("air_speed") String airSpeed,
                            @Nullable @SerializedName("total_air_acceleration") String totalAirAcceleration,
                            @Nullable @SerializedName("sh_fh_shff_fhff_frames") String shFhShffFhffFrames,
                            @Nullable @SerializedName("fall_speed_fast_fall_speed") String fallSpeedFastFallSpeed,
                            @Nonnull @SerializedName("oos_options") List<String> oosOptions,
                            @Nullable @SerializedName("shield_grab") String shieldGrab,
                            @Nullable @SerializedName("shield_drop") String shieldDrop,
                            @Nullable @SerializedName("jump_squat") String jumpSquat) {
    }
}
