package com.github.gpluscb.smashggnotifications.command.admin;

import com.github.gpluscb.smashggnotifications.command.Command;
import com.github.gpluscb.smashggnotifications.command.CommandContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class StatusCommand implements Command {
    private static final Logger log = LogManager.getLogger(StatusCommand.class);

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.hasAdminPermission()) return;

        if (ctx.getArgNum() < 2) {
            ctx.reply("Too few args. `status <ACTIVITY(listening|watching|playing)> <STATUS...>`").queue();
            return;
        }

        String activityString = ctx.getArg(0);
        Activity.ActivityType activityType;
        switch (activityString.toLowerCase()) {
            case "listening":
                activityType = Activity.ActivityType.LISTENING;
                break;
            case "watching":
                activityType = Activity.ActivityType.WATCHING;
                break;
            case "playing":
                activityType = Activity.ActivityType.DEFAULT;
                break;
            default:
                ctx.reply("Unknown activity. `status <ACTIVITY(listening|watching|playing)> <STATUS...>`").queue();
                return;
        }

        String newStatus = ctx.getArgsFrom(1);

        JDA jda = ctx.getJDA();
        ShardManager shardManager = jda.getShardManager();
        Activity activity = Activity.of(activityType, newStatus);
        if (shardManager == null) jda.getPresence().setActivity(activity);
        else shardManager.setActivity(activity);

        ctx.reply("Alrighty, changing the status now.").queue();

        log.info("Status changed to {}/{}", activityType, newStatus);
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"setstatus", "status"};
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
