package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.MiscUtil;
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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

            Duration duration;
            if (ctx.getArgNum() > 0) {
                duration = MiscUtil.parseDuration(ctx.getArgsFrom(0));
                if (duration == null) {
                    ctx.reply("The given duration was not a valid duration. An example duration is `1h 30m`.").queue();
                    return;
                }

                if (duration.isNegative()) {
                    ctx.reply("The duration must be positive, I can't time travel at this point in time unfortunately.").queue();
                    return;
                }

                if (duration.compareTo(Duration.ofHours(12)) > 0) {
                    ctx.reply("The maximum duration is 12h.").queue();
                    return;
                }
            } else {
                duration = null;
            }

            Member member = ctx.getMember();

            // We know member isn't null because this is in a guild.
            //noinspection ConstantConditions
            if (member.getRoles().contains(role)) {
                if (duration != null) {
                    ctx.reply("You already have the role. If you want me to remove the role, use this command again without a duration.").queue();
                    return;
                }

                try {
                    guild.removeRoleFromMember(ctx.getMember(), role).flatMap(v -> ctx.reply("I have successfully removed the role.")).queue();
                } catch (InsufficientPermissionException | HierarchyException e) {
                    ctx.reply("I couldn't remove the role because I lack permissions. Mods might be able to fix that.").queue();
                }
            } else {
                try {
                    guild.addRoleToMember(member, role).queue(v -> {
                        StringBuilder reply = new StringBuilder("I have successfully given you the role");
                        if (duration == null) {
                            reply.append(". Use this command again to remove it.");
                        } else {
                            reply.append(String.format(" for %s." +
                                    " Note that in case I reboot during that time, I won't be able to remove the role.", MiscUtil.durationToString(duration)));

                            guild.removeRoleFromMember(member, role).queueAfter(duration.toMillis(), TimeUnit.MILLISECONDS);
                        }

                        ctx.reply(reply.toString()).queue();
                    });
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