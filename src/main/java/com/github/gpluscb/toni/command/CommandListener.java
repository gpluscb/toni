package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.Config;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

public class CommandListener extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(CommandListener.class);

    @Nonnull
    private final CommandDispatcher dispatcher;
    @Nonnull
    private final Pattern prefixPattern;
    @Nonnull
    private final Config config;

    public CommandListener(@Nonnull CommandDispatcher dispatcher, @Nonnull Config config) {
        this.dispatcher = dispatcher;
        this.config = config;
        prefixPattern = Pattern.compile(String.format("(!t|toni|noti|<@!?%s>),? [\\s\\S]*", config.botId()), Pattern.CASE_INSENSITIVE);
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        try {
            if (event.isFromGuild() && !event.getGuildChannel().canTalk()) return;
        } catch (NullPointerException e) {
            // For forum channels, canTalk will NPE
            // I only really need this until end of the month so I mean yea I hate this solution
            // But eh
            return;
        }
        if (event.getAuthor().isBot() || !prefixPattern.matcher(event.getMessage().getContentRaw()).matches()) return;

        CommandContext<?> ctx = CommandContext.fromMessageReceivedEvent(event, config);
        log.trace("Correct prefix received - ctx: {}", ctx);

        dispatcher.dispatch(ctx);
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        CommandContext<?> ctx = CommandContext.fromSlashCommandEvent(event, config);
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
