package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;

public class AvailableCommand implements Command {
    @Nonnull
    private static final Logger log = LogManager.getLogger(AvailableCommand.class);

    @Nonnull
    private final UnrankedManager manager;

    public AvailableCommand(@Nonnull UnrankedManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers. And we are not in a server right now. We are in DMs.").queue();
            return;
        }

        Guild guild = ctx.getEvent().getGuild();
        long guildId = guild.getIdLong();
        try {
            UnrankedManager.MatchmakingConfig config = manager.loadMatchmakingConfig(guildId);
            if (config == null) {
                ctx.reply("Looks like matchmaking isn't set up in this server. Moderators can use the `unrankedcfg` command to set it up.").queue();
                return;
            }

            long roleId = config.getLfgRoleId();
            Role role = guild.getRoleById(roleId);

            if (role == null) {
                try {
                    manager.deleteMatchmakingConfig(guildId);
                    ctx.reply("Apparently, the matchmaking role has been deleted. I have reset the configuration for this guild now.").queue();
                } catch (SQLException e) {
                    log.error("This is really bad: we have an inconsistent state in the db (role deleted), and the auto fix failed. Guild: {}, Role: {}", guildId, roleId);
                    log.catching(e);
                    ctx.reply("Something went *really* wrong: it appears the matchmaking role I know has been deleted, but trying to reset the config for this server failed." +
                            " This might fix itself if you use this command again, or if you use `toni, unrankedcfg reset`. If it doesn't, please contact my dev." +
                            " I've told them already but it's probably good if they have some more context.").queue();
                }

                return;
            }

            Member member = ctx.getMember();

            // We know member isn't null because this is in a guild.
            //noinspection ConstantConditions
            if (member.getRoles().contains(role)) {
                try {
                    guild.removeRoleFromMember(ctx.getMember(), role).flatMap(v -> ctx.reply("I have successfully removed the role.")).queue();
                } catch (InsufficientPermissionException | HierarchyException e) {
                    ctx.reply("I couldn't remove the role because I lack permissions. Mods might be able to fix that.").queue();
                }
            } else {
                try {
                    guild.addRoleToMember(ctx.getMember(), role).flatMap(v -> ctx.reply("I have successfully given you the role. Use this command again to remove it.")).queue();
                } catch (InsufficientPermissionException | HierarchyException e) {
                    ctx.reply("I couldn't give you the role because I lack permissions. Mods might be able to fix that.").queue();
                }
            }
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Oops, something went horribly wong talking to the database." +
                    " I've told my dev, if this keeps happening you can give them some context too.").queue();
        }
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"available"};
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
