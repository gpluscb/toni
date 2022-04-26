package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CommandDispatcher {
    private static final Logger log = LogManager.getLogger(CommandDispatcher.class);

    @Nonnull
    private final List<CommandCategory> commands;
    @Nonnull
    private final ExecutorService executor;

    public CommandDispatcher(@Nonnull List<CommandCategory> commands) {
        this.commands = commands;
        executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("CommandPool [%d] Command-Thread", i.getAndIncrement()));
            }
        });
    }

    public void dispatch(@Nonnull CommandContext<?> ctx) {
        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        Command command = context.map(msg -> findMsgCommandByName(msg.getInvokedName()),
                slash -> findSlashCommandByName(slash.getName()));

        if (command != null) {
            context.onT(msg -> msg.reply("Commands invoked by a prefix (e.g. `toni`, `!t`) or mention are being phased out in favour of slash commands. " +
                    "These prefix commands might stop working after April, so please switch to slash commands until then. " +
                    "To use slash commands, just type `/` and a selection of slash commands should appear.").queue());

            boolean isFromGuild = context.map(msg -> msg.getEvent().isFromGuild(), slash -> slash.getEvent().isFromGuild());
            if (isFromGuild) {
                Permission[] perms = command.getInfo().requiredBotPerms();
                Guild guild = context.map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild());
                GuildMessageChannel channel = context.map(msg -> msg.getEvent().getGuildChannel(), slash -> slash.getEvent().getGuildChannel());

                if (!guild.getSelfMember().hasPermission(channel, perms)) {
                    log.debug("Missing perms: {}", (Object) perms);
                    ctx.reply(String.format("I need the following permissions for this command: %s.",
                                    Arrays.stream(perms).map(MiscUtil::getPermName).collect(Collectors.joining(", "))))
                            .queue();
                    return;
                }
            }

            synchronized (executor) {
                if (!executor.isShutdown()) {
                    log.trace("Dispatching command: {}", command);
                    executor.execute(FailLogger.logFail(() -> executeCommandSafe(command, ctx)));
                } else log.info("Rejecting dispatch of command {} - already shut down", command);
            }
        }
    }

    private void executeCommandSafe(@Nonnull Command command, @Nonnull CommandContext<?> ctx) {
        try {
            command.execute(ctx);
        } catch (Exception e) {
            log.error(String.format("Command %s had uncaught exception, ctx: %s", command, ctx), e);
            ctx.reply("One of my commands had a really bad error... I'll go yell at my dev about it (at least if they managed to implement that feature right), but you should give them some context too.").queue();
        }
    }

    public void dispatchAutoComplete(@Nonnull CommandAutoCompleteInteractionEvent event) {
        Command command = findSlashCommandByName(event.getName());
        if (command == null) {
            log.error("Auto complete event was received, but no corresponding command was found: {}", event.getName());
            return;
        }

        List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = command.onAutocomplete(event);
        event.replyChoices(choices).queue();
    }

    @Nullable
    private Command findMsgCommandByName(@Nonnull String name) {
        return commands.stream().flatMap(category -> category.commands().stream())
                .filter(command -> Arrays.asList(command.getInfo().aliases()).contains(name.toLowerCase()))
                .findAny()
                .orElse(null);
    }

    @Nullable
    private Command findSlashCommandByName(@Nonnull String name) {
        return commands.stream().flatMap(category -> category.commands().stream())
                .filter(command -> command.getInfo().commandData().getName().equals(name))
                .findAny()
                .orElse(null);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
