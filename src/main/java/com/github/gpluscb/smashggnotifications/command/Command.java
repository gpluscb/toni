package com.github.gpluscb.smashggnotifications.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface Command {
    void execute(@Nonnull CommandContext ctx);

    @Nonnull
    String[] getAliases();

    /**
     * @return null if this command should not be displayed by the category help command
     */
    @Nullable
    String getShortHelp();

    /**
     * @return null if this command should not be displayed by the command specific help command
     */
    @Nullable
    String getDetailedHelp();
}
