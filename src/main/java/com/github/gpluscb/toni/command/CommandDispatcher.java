package com.github.gpluscb.toni.command;

import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Arrays;
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

    public void dispatch(@Nonnull CommandContext ctx) {
        commands.stream().flatMap(category -> category.getCommands().stream())
                .filter(command ->
                        ctx.getContext().map(
                                msg -> Arrays.asList(command.getInfo().getAliases()).contains(msg.getInvokedName().toLowerCase()),
                                slash -> command.getInfo().getCommandData().getName().equals(slash.getName())
                        )
                ).findAny()
                .ifPresent(command -> {
                    OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
                    if (context.isT()) {
                        MessageCommandContext msg = context.getTOrThrow();

                        MessageReceivedEvent e = msg.getEvent();
                        Permission[] perms = command.getInfo().getRequiredBotPerms();
                        if (e.isFromGuild() && !e.getGuild().getSelfMember().hasPermission(e.getTextChannel(), perms)) {
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
                });
    }

    private void executeCommandSafe(@Nonnull Command command, @Nonnull CommandContext ctx) {
        try {
            command.execute(ctx);
        } catch (Exception e) {
            log.error(String.format("Command %s had uncaught exception, ctx: %s", command, ctx), e);
            ctx.reply("One of my commands had a really bad error... I'll go yell at my dev about it (at least if they managed to implement that feature right), but you should give them some context too.").queue();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
