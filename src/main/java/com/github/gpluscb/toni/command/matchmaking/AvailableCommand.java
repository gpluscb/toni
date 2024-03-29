package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.db.DBManager;
import com.github.gpluscb.toni.util.MiscUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AvailableCommand implements Command {
    @Nonnull
    private static final Logger log = LogManager.getLogger(AvailableCommand.class);

    @Nonnull
    private final DBManager manager;

    @Nonnull
    private final Map<Long, ScheduledFuture<?>> scheduledRoleRemovals;

    public AvailableCommand(@Nonnull DBManager manager) {
        this.manager = manager;
        scheduledRoleRemovals = new HashMap<>();
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers. And we are not in a server right now. We are in DMs.").queue();
            return;
        }

        Guild guild = ctx.getEvent().getGuild();
        @SuppressWarnings("ConstantConditions") // Checked for isFromGuild already
        long guildId = guild.getIdLong();
        try {
            DBManager.MatchmakingConfig config = manager.loadMatchmakingConfig(guildId);
            if (config == null) {
                ctx.reply("Looks like matchmaking isn't set up in this server. Moderators can use the `unrankedcfg` command to set it up.").queue();
                return;
            }

            long roleId = config.lfgRoleId();
            Role role = guild.getRoleById(roleId);

            if (role == null) {
                try {
                    manager.deleteMatchmakingConfig(guildId);
                    ctx.reply("Apparently, the matchmaking role has been deleted. I have reset the configuration for this guild now.").queue();
                } catch (SQLException e) {
                    log.error("This is really bad: we have an inconsistent state in the db (role deleted), and the auto fix failed. Guild: {}, Role: {}", guildId, roleId);
                    log.catching(e);
                    ctx.reply("Something went *really* wrong: it appears the matchmaking role I know has been deleted, but trying to reset the config for this server failed." +
                            " This might fix itself if you use this command again, or if you use `/unrankedcfg reset`. If it doesn't, please contact my dev." +
                            " I've told them already but it's probably good if they have some more context.").queue();
                }

                return;
            }

            Duration duration;
            OptionMapping durationMapping = ctx.getOption("duration");
            if (durationMapping != null) {
                duration = MiscUtil.parseDuration(durationMapping.getAsString());
                if (duration == null) {
                    ctx.reply("The given duration was not a valid duration. An example duration is `1h 30m`.").queue();
                    return;
                }
            } else duration = null;

            Member member = ctx.getMember();
            // We know member isn't null because this is in a guild.
            //noinspection ConstantConditions
            long id = member.getIdLong();

            if (member.getRoles().contains(role)) {
                if (duration != null) {
                    ctx.reply("You already have the role. If you want me to remove the role, use this command again without a duration.").queue();
                    return;
                }

                try {
                    synchronized (scheduledRoleRemovals) {
                        ScheduledFuture<?> future = scheduledRoleRemovals.get(id);
                        if (future != null) future.cancel(false);
                        scheduledRoleRemovals.remove(id);
                    }

                    guild.removeRoleFromMember(ctx.getMember(), role).flatMap(v -> (RestAction<?>) ctx.reply("I have successfully removed the role.")).queue();
                } catch (InsufficientPermissionException | HierarchyException e) {
                    ctx.reply("I couldn't remove the role because I lack permissions. Mods might be able to fix that.").queue();
                }
            } else {
                synchronized (scheduledRoleRemovals) {
                    ScheduledFuture<?> future = scheduledRoleRemovals.get(id);

                    // We don't have the role, but we have role removal scheduled
                    // Maybe it was removed by a moderator?
                    // Either way, let's just silently cancel the scheduled role removal.
                    if (future != null) future.cancel(false);
                    scheduledRoleRemovals.remove(id);
                }

                try {
                    guild.addRoleToMember(member, role).queue(v -> {
                        StringBuilder reply = new StringBuilder("I have successfully given you the role");
                        if (duration == null) {
                            reply.append(". Use this command again to remove it.");
                        } else {
                            reply.append(String.format(" for %s (until %s)." +
                                            " Note that in case I reboot during that time, I won't be able to remove the role.",
                                    MiscUtil.durationToString(duration),
                                    TimeFormat.RELATIVE.after(duration)));

                            ScheduledFuture<?> future = guild.removeRoleFromMember(member, role)
                                    .mapToResult()
                                    .queueAfter(duration.toMillis(), TimeUnit.MILLISECONDS, result -> {
                                        synchronized (scheduledRoleRemovals) {
                                            scheduledRoleRemovals.remove(id);
                                        }

                                        result.onFailure(RestAction.getDefaultFailure());
                                    });

                            synchronized (scheduledRoleRemovals) {
                                scheduledRoleRemovals.put(id, future);
                            }
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
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setShortHelp("Gives you the matchmaking role for a given amount of time.")
                .setDetailedHelp("""
                        Gives you the matchmaking role for the given duration, or permanently if you don't specify a duration. The duration can have the format `Xh Xm Xs`, and it can't be longer than 12h. Note that I can't remember to remove the role if I shut down during that time.
                        Slash command options:
                        • (Optional) `duration`: The duration after which I should remove the matchmaking role again. By default I'll remove it only after you use this command again.""") // TODO: Maybe fix the shutdown thing? That sounds so painful to fix tho.
                .setCommandData(Commands.slash("available", "Gives you the matchmaking role for a given duration")
                        .addOption(OptionType.STRING, "duration", "The duration after which I should remove the matchmaking role. Default is to never remove it", false))
                .build();
    }
}
