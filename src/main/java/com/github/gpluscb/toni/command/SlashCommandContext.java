package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

import javax.annotation.Nonnull;

public class SlashCommandContext {
    @Nonnull
    private final SlashCommandEvent event;

    public SlashCommandContext(@Nonnull SlashCommandEvent event) {
        this.event = event;
    }


}
