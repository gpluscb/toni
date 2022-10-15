package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

import javax.annotation.Nonnull;
import java.util.List;

public interface Command {
    void execute(@Nonnull CommandContext ctx);

    @Nonnull
    CommandInfo getInfo();

    @Nonnull
    default List<net.dv8tion.jda.api.interactions.commands.Command.Choice> onAutocomplete(@Nonnull CommandAutoCompleteInteractionEvent event) {
        throw new UnsupportedOperationException("onAutocomplete called without implementation");
    }
}
