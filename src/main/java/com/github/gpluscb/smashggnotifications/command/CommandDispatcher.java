package com.github.gpluscb.smashggnotifications.command;

import com.github.gpluscb.smashggnotifications.util.FailLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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
                .filter(command -> Arrays.asList(command.getAliases()).contains(ctx.getInvokedName().toLowerCase())).findAny()
                .ifPresent(command -> {
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
