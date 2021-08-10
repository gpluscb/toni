package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.util.discord.DMChoiceWaiter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

public class CommandListener extends ListenerAdapter {
    private final Logger log = LogManager.getLogger(CommandListener.class);

    @Nonnull
    private final DMChoiceWaiter waiter;
    @Nonnull
    private final CommandDispatcher dispatcher;
    @Nonnull
    private final Pattern prefixPattern;

    public CommandListener(@Nonnull DMChoiceWaiter waiter, @Nonnull CommandDispatcher dispatcher, long botId) {
        this.waiter = waiter;
        this.dispatcher = dispatcher;
        prefixPattern = Pattern.compile(String.format("(!t|toni|noti|<@!?%s>),? [\\s\\S]*", botId), Pattern.CASE_INSENSITIVE);
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.isFromGuild() && !event.getTextChannel().canTalk()) return;
        // Don't listen to commands when currently listening to DM things in DMs
        if (!event.isFromGuild() && waiter.getActiveUsers().contains(event.getAuthor().getIdLong())) return;
        if (event.getAuthor().isBot() || !prefixPattern.matcher(event.getMessage().getContentRaw()).matches()) return;

        CommandContext ctx = new CommandContext(event);
        log.trace("Correct prefix received - ctx: {}", ctx);

        dispatcher.dispatch(ctx);
    }
}
