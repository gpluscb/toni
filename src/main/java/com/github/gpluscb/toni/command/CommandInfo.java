package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CommandInfo {
    private final boolean adminOnly;
    @Nonnull
    private final Permission[] requiredBotPerms;
    @Nonnull
    private final String[] aliases;
    @Nonnull
    private final CommandData commandData;
    @Nullable
    private final String shortHelp;
    @Nullable
    private final String detailedHelp;

    public CommandInfo(boolean adminOnly, @Nonnull Permission[] requiredBotPerms, @Nonnull String[] aliases, @Nonnull CommandData commandData, @Nullable String shortHelp, @Nullable String detailedHelp) {
        this.adminOnly = adminOnly;
        this.requiredBotPerms = requiredBotPerms;
        this.aliases = aliases;
        this.commandData = commandData;
        this.shortHelp = shortHelp;
        this.detailedHelp = detailedHelp;
    }

    @Nonnull
    public Permission[] getRequiredBotPerms() {
        return requiredBotPerms;
    }

    public boolean isAdminGuildOnly() {
        return adminOnly;
    }

    @Nonnull
    public String[] getAliases() {
        return aliases;
    }

    @Nonnull
    public CommandData getCommandData() {
        return commandData;
    }

    /**
     * @return null if this command should not be displayed by the category help command
     */
    @Nullable
    public String getShortHelp() {
        return shortHelp;
    }

    /**
     * @return null if this command should not be displayed by the command specific help command
     */
    @Nullable
    public String getDetailedHelp() {
        return detailedHelp;
    }

    public static class Builder {
        private boolean adminOnly;
        @Nonnull
        private Permission[] requiredBotPerms;
        @Nullable
        private String[] aliases;
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
        public Builder setAliases(@Nonnull String[] aliases) {
            this.aliases = aliases;
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
            if (aliases == null || commandData == null)
                throw new IllegalStateException("All fields must be set");

            return new CommandInfo(adminOnly, requiredBotPerms, aliases, commandData, shortHelp, detailedHelp);
        }
    }
}
