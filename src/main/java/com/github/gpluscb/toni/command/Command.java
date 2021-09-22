package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

import javax.annotation.Nonnull;

// TODO: Neat system for getting arguments out of CommandContext with defaults optionals and such and such
// Tried in the other branch but abandoned for now, seems not worth the effort
public interface Command {
    void execute(@Nonnull MessageCommandContext ctx);

    void execute(@Nonnull SlashCommandEvent event);

    @Nonnull
    CommandInfo getInfo();
}
