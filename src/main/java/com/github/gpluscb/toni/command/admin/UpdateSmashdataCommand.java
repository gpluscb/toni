package com.github.gpluscb.toni.command.admin;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
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
        if (!ctx.memberHasBotAdminPermission()) return;

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
