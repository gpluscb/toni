package com.github.gpluscb.toni.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO: Neat system for getting arguments out of CommandContext with defaults optionals and such and such
// Tried in the other branch but abandoned for now, seems not worth the effort
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
