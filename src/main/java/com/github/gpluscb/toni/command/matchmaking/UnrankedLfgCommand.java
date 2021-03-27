package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.time.Duration;

public class UnrankedLfgCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedLfgCommand.class);

    @Nonnull
    private final UnrankedManager manager;

    public UnrankedLfgCommand(@Nonnull UnrankedManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers. There is only us two in our DMs. And it's nice that you want to play with me, but sorry I don't have hands.").queue();
            return;
        }

        try {
            UnrankedManager.MatchmakingConfig config = manager.loadMatchmakingConfig(ctx.getEvent().getGuild().getIdLong());
            if (config == null) {
                ctx.reply("Matchmaking is not set up in this server. A mod can use the `unrankedcfg` command to configure matchmaking.").queue();
                return;
            }

            Long channelId = config.getChannelId();
            if (channelId != null && ctx.getChannel().getIdLong() != channelId) {
                ctx.reply(String.format("This command is configured to only work in %s.", MiscUtil.mentionChannel(channelId))).queue();
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

                if (duration.compareTo(Duration.ofHours(5)) > 0) {
                    ctx.reply("The maximum duration is 5h.").queue();
                    return;
                }
            } else {
                duration = Duration.ofHours(2);
            }

            long userId = ctx.getAuthor().getIdLong();
            long roleId = config.getLfgRoleId();
            ctx.reply(String.format("%s, %s is looking for a game for %s. React with %s to accept.", MiscUtil.durationToString(duration), MiscUtil.mentionRole(roleId), MiscUtil.mentionUser(userId), Constants.FENCER))
                    .mentionRoles(roleId).mentionUsers(userId).queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went wrong talking to my database. I've told my dev about this, if this keeps happening you should give them some context too.").queue();
        }
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"unranked", "lfg"};
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
