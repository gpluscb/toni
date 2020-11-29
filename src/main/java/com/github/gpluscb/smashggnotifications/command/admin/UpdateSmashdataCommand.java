package com.github.gpluscb.smashggnotifications.command.admin;

import com.github.gpluscb.smashggnotifications.command.Command;
import com.github.gpluscb.smashggnotifications.command.CommandContext;
import com.github.gpluscb.smashggnotifications.smashdata.SmashdataManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;

public class UpdateSmashdataCommand implements Command {
    private static final Logger log = LogManager.getLogger(UpdateSmashdataCommand.class);

    @Nonnull
    private final SmashdataManager smashdata;

    public UpdateSmashdataCommand(@Nonnull SmashdataManager smashdata) {
        this.smashdata = smashdata;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.hasAdminPermission()) return;

        if (ctx.getArgNum() < 1) {
            ctx.reply("Too few args. `updatesmashdata <PATH TO NEW DB>`").queue();
            return;
        }

        String dbPath = ctx.getArgsFrom(0);

        try {
            smashdata.updateDb(dbPath);
        } catch (SQLException e) {
            log.error(e);
            ctx.reply("SQLException, see logs").queue();
        }

        ctx.reply("Done").queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"updatesmashdata"};
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
