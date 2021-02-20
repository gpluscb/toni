package com.github.gpluscb.toni.command.admin;

import com.github.gpluscb.toni.Bot;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class ShutdownCommand implements Command {
    private static final Logger log = LogManager.getLogger(ShutdownCommand.class);

    @Nonnull
    private final Bot bot;

    public ShutdownCommand(@Nonnull Bot bot) {
        this.bot = bot;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.hasAdminPermission()) return;

        log.info("Shutdown command executed - shutting down");
        ctx.reply("Alrighty, shutting down!").timeout(3, TimeUnit.SECONDS).queue(m -> bot.shutdown(), t -> {
            log.catching(t);
            bot.shutdown();
        });
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"shutdown", "shut"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return null;
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return null;
    }
}
