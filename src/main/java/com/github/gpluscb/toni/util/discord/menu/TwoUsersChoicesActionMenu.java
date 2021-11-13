package com.github.gpluscb.toni.util.discord.menu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TwoUsersChoicesActionMenu extends ActionMenu {
    @Nonnull
    private final Settings settings;

    public TwoUsersChoicesActionMenu(@Nonnull Settings settings) {
        super(settings.actionMenuSettings());
        this.settings = settings;
    }

    @Nonnull
    public Settings getTwoUsersChoicesActionMenuSettings() {
        return settings;
    }

    public abstract class TwoUsersMenuStateInfo extends MenuStateInfo {
        @Nonnull
        public Settings getTwoUsersChoicesActionMenuSettings() {
            return settings;
        }
    }

    public record Settings(@Nonnull ActionMenu.Settings actionMenuSettings, long user1, long user2) {
        public static class Builder {
            @Nullable
            private ActionMenu.Settings actionMenuSettings;
            @Nullable
            private Long user1;
            @Nullable
            private Long user2;

            @Nonnull
            public Builder setActionMenuSettings(@Nonnull ActionMenu.Settings actionMenuSettings) {
                this.actionMenuSettings = actionMenuSettings;
                return this;
            }

            @Nonnull
            public Builder setUsers(long user1, long user2) {
                this.user1 = user1;
                this.user2 = user2;
                return this;
            }

            public Settings build() {
                if (actionMenuSettings == null) throw new IllegalStateException("ActionMenuSettings must be set");
                if (user1 == null || user2 == null) throw new IllegalStateException("Users must be set");
                return new Settings(actionMenuSettings, user1, user2);
            }
        }
    }
}
