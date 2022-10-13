package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

public class CommandListener extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(CommandListener.class);

    @Nonnull
    private final CommandDispatcher dispatcher;
    @Nonnull
    private final Config config;

    public CommandListener(@Nonnull CommandDispatcher dispatcher, @Nonnull Config config) {
        this.dispatcher = dispatcher;
        this.config = config;
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getMessage().getContentRaw().matches(String.format("<@!?%d>(,? help)?", config.botId()))) {
            // TODO: Mention help command
            event.getMessage().reply("Hi! For bot help, use the `/help` slash command").queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        CommandContext ctx = new CommandContext(event, config);
        if (event.isFromGuild() && !event.getGuildChannel().canTalk()) {
            event.reply("I don't have permissions in this channel.").queue();
            return;
        }

        log.trace("Slash command - ctx: {}", ctx);

        dispatcher.dispatch(ctx);
    }

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        dispatcher.dispatchAutoComplete(event);
    }
}
