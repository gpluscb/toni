package com.github.gpluscb.toni.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO: Neat system for getting arguments out of CommandContext with defaults optionals and such and such
// Tried in the other branch but abandoned for now, seems not worth the effort
public interface Command {
    void execute(@Nonnull MessageCommandContext ctx);

    void execute(@Nonnull SlashCommandEvent event);

    @Nonnull
    default Permission[] getRequiredBotPerms() {
        return new Permission[0];
    }

    @Nonnull
    String[] getAliases();

    @Nonnull
    CommandData getCommandData();

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
