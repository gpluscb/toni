package com.github.gpluscb.toni.command.admin;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;

@SuppressWarnings("ClassCanBeRecord")
public class UpdateSmashdataCommand implements Command {
    private static final Logger log = LogManager.getLogger(UpdateSmashdataCommand.class);

    @Nonnull
    private final SmashdataManager smashdata;

    public UpdateSmashdataCommand(@Nonnull SmashdataManager smashdata) {
        this.smashdata = smashdata;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        if (!ctx.memberHasBotAdminPermission()) return;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT() && context.getTOrThrow().getArgNum() < 1) {
            ctx.reply("Too few args. `updatesmashdata <PATH TO NEW DB>`").queue();
            return;
        }

        String dbPath = ctx.getContext().map(msg -> msg.getArgsFrom(0), slash -> slash.getOptionNonNull("path").getAsString());

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
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAdminOnly(true)
                .setAliases(new String[]{"updatesmashdata"})
                .setCommandData(new CommandData("updatesmashdata", "Updates the smashdata db path")
                        .addOption(OptionType.STRING, "path", "The new path", true))
                .build();
    }
}
