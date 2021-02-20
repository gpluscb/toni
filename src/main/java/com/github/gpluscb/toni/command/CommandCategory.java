package com.github.gpluscb.toni.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CommandCategory {
    @Nullable
    private final String categoryName;
    @Nullable
    private final String shortDescription;
    @Nonnull
    private final List<Command> commands;

    public CommandCategory(@Nullable String categoryName, @Nullable String shortDescription, @Nonnull List<Command> commands) {
        this.categoryName = categoryName;
        this.shortDescription = shortDescription;
        this.commands = commands;
    }

    /**
     * @return null if cat should not show in help
     */
    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * @return null if cat should not show in help
     */
    @Nullable
    public String getShortDescription() {
        return shortDescription;
    }

    @Nonnull
    public List<Command> getCommands() {
        return commands;
    }
}
