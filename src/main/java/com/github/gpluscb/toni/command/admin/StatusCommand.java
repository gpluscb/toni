package com.github.gpluscb.toni.command.admin;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

public class StatusCommand implements Command {
    private static final Logger log = LogManager.getLogger(StatusCommand.class);

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        if (!ctx.memberHasBotAdminPermission()) return;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT() && context.getTOrThrow().getArgNum() < 2) {
            ctx.reply("Too few args. `status <ACTIVITY(listening|watching|playing|competing)> <STATUS...>`").queue();
            return;
        }

        String activityString = context.map(msg -> msg.getArg(0), slash -> slash.getOptionNonNull("activity").getAsString());

        Activity.ActivityType activityType;
        switch (activityString.toLowerCase()) {
            case "listening" -> activityType = Activity.ActivityType.LISTENING;
            case "watching" -> activityType = Activity.ActivityType.WATCHING;
            case "playing" -> activityType = Activity.ActivityType.PLAYING;
            case "competing" -> activityType = Activity.ActivityType.COMPETING;
            default -> {
                ctx.reply("Unknown activity. `status <ACTIVITY(listening|watching|playing|competing)> <STATUS...>`").queue();
                return;
            }
        }

        String newStatus = context.map(msg -> msg.getArgsFrom(1), slash -> slash.getOptionNonNull("status").getAsString());

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
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAdminOnly(true)
                .setAliases(new String[]{"setstatus", "status"})
                .setCommandData(Commands.slash("status", "Changes the bot status")
                        .addOptions(new OptionData(OptionType.STRING, "activity", "The displayed activity", true)
                                .addChoice("listening", "listening")
                                .addChoice("watching", "watching")
                                .addChoice("playing", "playing")
                                .addChoice("competing", "competing"))
                        .addOption(OptionType.STRING, "status", "The displayed status", true))
                .build();
    }
}
