package com.github.gpluscb.toni.ultimateframedata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CharacterData {
    @Nonnull
    private final String ufdUrl;
    @Nonnull
    private final String name;
    @Nonnull
    private final List<MoveData> normals;
    @Nonnull
    private final List<MoveData> aerials;
    @Nonnull
    private final List<MoveData> specials;
    @Nonnull
    private final List<MoveData> grabs;
    @Nonnull
    private final List<MoveData> dodges;
    @Nonnull
    private final MiscData miscData;

    public CharacterData(@Nonnull String ufdUrl, @Nonnull String name, @Nonnull List<MoveData> normals, @Nonnull List<MoveData> aerials, @Nonnull List<MoveData> specials, @Nonnull List<MoveData> grabs, @Nonnull List<MoveData> dodges, @Nonnull MiscData miscData) {
        this.ufdUrl = ufdUrl;
        this.name = name;
        this.normals = normals;
        this.aerials = aerials;
        this.specials = specials;
        this.grabs = grabs;
        this.dodges = dodges;
        this.miscData = miscData;
    }

    @Nonnull
    public String getUfdUrl() {
        return ufdUrl;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public List<MoveData> getMoves(@Nonnull MoveSection section) {
        switch (section) {
            case NORMALS:
                return normals;
            case AERIALS:
                return aerials;
            case SPECIALS:
                return specials;
            case GRABS:
                return grabs;
            case DODGES:
                return dodges;
            case MISC:
                return miscData.getMoves();
            default:
                throw new IllegalStateException("Nothing matches");
        }
    }

    @Nonnull
    public List<MoveData> getNormals() {
        return normals;
    }

    @Nonnull
    public List<MoveData> getAerials() {
        return aerials;
    }

    @Nonnull
    public List<MoveData> getSpecials() {
        return specials;
    }

    @Nonnull
    public List<MoveData> getGrabs() {
        return grabs;
    }

    @Nonnull
    public List<MoveData> getDodges() {
        return dodges;
    }

    @Nonnull
    public MiscData getMiscData() {
        return miscData;
    }

    public enum MoveSection {
        NORMALS,
        AERIALS,
        SPECIALS,
        GRABS,
        DODGES,
        MISC;

        @Nonnull
        public MoveSection next() {
            switch (this) {
                case NORMALS:
                    return AERIALS;
                case AERIALS:
                    return SPECIALS;
                case SPECIALS:
                    return GRABS;
                case GRABS:
                    return DODGES;
                case DODGES:
                    return MISC;
                case MISC:
                    return NORMALS;
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public MoveSection prev() {
            switch (this) {
                case NORMALS:
                    return MISC;
                case AERIALS:
                    return NORMALS;
                case SPECIALS:
                    return AERIALS;
                case GRABS:
                    return SPECIALS;
                case DODGES:
                    return GRABS;
                case MISC:
                    return DODGES;
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public List<CharacterData.MoveData> getMoveData(@Nonnull CharacterData data) {
            switch (this) {
                case NORMALS:
                    return data.getNormals();
                case AERIALS:
                    return data.getAerials();
                case SPECIALS:
                    return data.getSpecials();
                case GRABS:
                    return data.getGrabs();
                case DODGES:
                    return data.getDodges();
                case MISC:
                    return data.getMiscData().getMoves();
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public String displayName() {
            switch (this) {
                case NORMALS:
                    return "Ground Move";
                case AERIALS:
                    return "Aerial";
                case SPECIALS:
                    return "Special";
                case GRABS:
                    return "Grab";
                case DODGES:
                    return "Dodge";
                case MISC:
                    return "Misc";
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }

        @Nonnull
        public String sectionHtmlId() {
            switch (this) {
                case NORMALS:
                    return "groundattacks";
                case AERIALS:
                    return "aerialattacks";
                case SPECIALS:
                    return "specialattacks";
                case GRABS:
                    return "grabs";
                case DODGES:
                    return "dodges";
                case MISC:
                    return "misc";
                default:
                    throw new IllegalStateException("Nothing matches");
            }
        }
    }

    public static class MoveData {
        @Nonnull
        private final List<HitboxData> hitboxes;
        @Nullable
        private final String moveName;
        @Nullable
        private final String startup;
        @Nullable
        private final String totalFrames;
        @Nullable
        private final String landingLag;
        @Nullable
        private final String notes;
        @Nullable
        private final String baseDamage;
        @Nullable
        private final String shieldLag;
        @Nullable
        private final String shieldStun;
        @Nullable
        private final String whichHitbox;
        @Nullable
        private final String advantage;
        @Nullable
        private final String activeFrames;

        public MoveData(@Nonnull List<HitboxData> hitboxes, @Nullable String moveName, @Nullable String startup, @Nullable String totalFrames, @Nullable String landingLag, @Nullable String notes, @Nullable String baseDamage, @Nullable String shieldLag, @Nullable String shieldStun, @Nullable String whichHitbox, @Nullable String advantage, @Nullable String activeFrames) {
            this.hitboxes = hitboxes;
            this.moveName = moveName;
            this.startup = startup;
            this.totalFrames = totalFrames;
            this.landingLag = landingLag;
            this.notes = notes;
            this.baseDamage = baseDamage;
            this.shieldLag = shieldLag;
            this.shieldStun = shieldStun;
            this.whichHitbox = whichHitbox;
            this.advantage = advantage;
            this.activeFrames = activeFrames;
        }

        @Nonnull
        public List<HitboxData> getHitboxes() {
            return hitboxes;
        }

        @Nullable
        public String getMoveName() {
            return moveName;
        }

        @Nullable
        public String getStartup() {
            return startup;
        }

        @Nullable
        public String getTotalFrames() {
            return totalFrames;
        }

        @Nullable
        public String getLandingLag() {
            return landingLag;
        }

        @Nullable
        public String getNotes() {
            return notes;
        }

        @Nullable
        public String getBaseDamage() {
            return baseDamage;
        }

        @Nullable
        public String getShieldLag() {
            return shieldLag;
        }

        @Nullable
        public String getShieldStun() {
            return shieldStun;
        }

        @Nullable
        public String getWhichHitbox() {
            return whichHitbox;
        }

        @Nullable
        public String getAdvantage() {
            return advantage;
        }

        @Nullable
        public String getActiveFrames() {
            return activeFrames;
        }
    }

    public static class HitboxData {
        @Nullable
        private final String name;
        @Nonnull
        private final String url;

        public HitboxData(@Nullable String name, @Nonnull String url) {
            this.name = name;
            this.url = url;
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Nonnull
        public String getUrl() {
            return url;
        }
    }

    public static class MiscData {
        @Nullable
        private final StatsData stats;
        @Nonnull
        private final List<MoveData> moves;

        public MiscData(@Nullable StatsData stats, @Nonnull List<MoveData> moves) {
            this.stats = stats;
            this.moves = moves;
        }

        @Nullable
        public StatsData getStats() {
            return stats;
        }

        @Nonnull
        public List<MoveData> getMoves() {
            return moves;
        }
    }

    public static class StatsData {
        @Nullable
        private final String weight;
        @Nullable
        private final String gravity;
        @Nullable
        private final String valkSpeed;
        @Nullable
        private final String runSpeed;
        @Nullable
        private final String initialDash;
        @Nullable
        private final String airSpeed;
        @Nullable
        private final String totalAirAcceleration;
        @Nullable
        private final String shFhShffFhffFrames;
        @Nullable
        private final String fallSpeedFastFallSpeed;
        @Nonnull
        private final List<String> oosOptions;
        @Nullable
        private final String shieldGrab;
        @Nullable
        private final String shieldDrop;
        @Nullable
        private final String jumpSquat;

        public StatsData(@Nullable String weight, @Nullable String gravity, @Nullable String valkSpeed, @Nullable String runSpeed, @Nullable String initialDash, @Nullable String airSpeed, @Nullable String totalAirAcceleration, @Nullable String shFhShffFhffFrames, @Nullable String fallSpeedFastFallSpeed, @Nonnull List<String> oosOptions, @Nullable String shieldGrab, @Nullable String shieldDrop, @Nullable String jumpSquat) {
            this.weight = weight;
            this.gravity = gravity;
            this.valkSpeed = valkSpeed;
            this.runSpeed = runSpeed;
            this.initialDash = initialDash;
            this.airSpeed = airSpeed;
            this.totalAirAcceleration = totalAirAcceleration;
            this.shFhShffFhffFrames = shFhShffFhffFrames;
            this.fallSpeedFastFallSpeed = fallSpeedFastFallSpeed;
            this.oosOptions = oosOptions;
            this.shieldGrab = shieldGrab;
            this.shieldDrop = shieldDrop;
            this.jumpSquat = jumpSquat;
        }

        @Nullable
        public String getWeight() {
            return weight;
        }

        @Nullable
        public String getGravity() {
            return gravity;
        }

        @Nullable
        public String getValkSpeed() {
            return valkSpeed;
        }

        @Nullable
        public String getRunSpeed() {
            return runSpeed;
        }

        @Nullable
        public String getInitialDash() {
            return initialDash;
        }

        @Nullable
        public String getAirSpeed() {
            return airSpeed;
        }

        @Nullable
        public String getTotalAirAcceleration() {
            return totalAirAcceleration;
        }

        @Nullable
        public String getShFhShffFhffFrames() {
            return shFhShffFhffFrames;
        }

        @Nullable
        public String getFallSpeedFastFallSpeed() {
            return fallSpeedFastFallSpeed;
        }

        @Nonnull
        public List<String> getOosOptions() {
            return oosOptions;
        }

        @Nullable
        public String getShieldGrab() {
            return shieldGrab;
        }

        @Nullable
        public String getShieldDrop() {
            return shieldDrop;
        }

        @Nullable
        public String getJumpSquat() {
            return jumpSquat;
        }
    }


}
