package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record CommandInfo(boolean adminOnly, @Nonnull Permission[] requiredBotPerms,
                          @Nonnull CommandData commandData,
                          @Nullable String shortHelp, @Nullable String detailedHelp) {
    public static class Builder {
        private boolean adminOnly;
        @Nonnull
        private Permission[] requiredBotPerms;
        @Nullable
        private CommandData commandData;
        @Nullable
        private String shortHelp;
        @Nullable
        private String detailedHelp;

        /**
         * Defaults: adminOnly - false, requiredBotPerms - empty
         */
        public Builder() {
            adminOnly = false;
            requiredBotPerms = new Permission[0];
        }

        @Nonnull
        public Builder setAdminOnly(boolean adminOnly) {
            this.adminOnly = adminOnly;
            return this;
        }

        @Nonnull
        public Builder setRequiredBotPerms(@Nonnull Permission[] requiredBotPerms) {
            this.requiredBotPerms = requiredBotPerms;
            return this;
        }

        @Nonnull
        public Builder setCommandData(@Nonnull CommandData commandData) {
            this.commandData = commandData;
            return this;
        }

        /**
         * null if this command should not be displayed by the category help command
         */
        @Nonnull
        public Builder setShortHelp(@Nullable String shortHelp) {
            this.shortHelp = shortHelp;
            return this;
        }

        /**
         * null if this command should not be displayed by the command specific help command
         */
        @Nonnull
        public Builder setDetailedHelp(@Nullable String detailedHelp) {
            this.detailedHelp = detailedHelp;
            return this;
        }

        @Nonnull
        public CommandInfo build() {
            if (commandData == null)
                throw new IllegalStateException("commandData must be set");

            return new CommandInfo(adminOnly, requiredBotPerms, commandData, shortHelp, detailedHelp);
        }
    }
}
