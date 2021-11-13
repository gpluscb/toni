package com.github.gpluscb.toni.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record CommandCategory(@Nullable String categoryName, @Nullable String shortDescription,
                              @Nonnull List<Command> commands) {
}
