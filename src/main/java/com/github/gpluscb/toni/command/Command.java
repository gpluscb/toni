package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

// TODO: Neat system for getting arguments out of CommandContext with defaults optionals and such and such
// Tried in the other branch but abandoned for now, seems not worth the effort
public interface Command {
    void execute(@Nonnull CommandContext<?> ctx);

    @Nonnull
    CommandInfo getInfo();

    @Nullable
    default List<net.dv8tion.jda.api.interactions.commands.Command.Choice> onAutocomplete(@Nonnull CommandAutoCompleteInteractionEvent event) {
        return null;
    }
}
