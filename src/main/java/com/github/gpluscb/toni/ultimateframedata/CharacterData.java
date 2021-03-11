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

    public CharacterData(@Nonnull String ufdUrl, @Nonnull String name, @Nonnull List<MoveData> normals, @Nonnull List<MoveData> aerials, @Nonnull List<MoveData> specials, @Nonnull List<MoveData> grabs, @Nonnull List<MoveData> dodges) {
        this.ufdUrl = ufdUrl;
        this.name = name;
        this.normals = normals;
        this.aerials = aerials;
        this.specials = specials;
        this.grabs = grabs;
        this.dodges = dodges;
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
}
