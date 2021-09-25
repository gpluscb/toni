package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;

public class PingCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        AtomicLong sendTime = new AtomicLong();
        ctx.getContext().map(
                msg ->
                        msg.reply("Doing the measurement...").addCheck(() -> {
                            sendTime.set(System.currentTimeMillis());
                            return true;
                        }).flatMap(m -> m.editMessage(calculateResponse(sendTime.get()))),
                slash ->
                        slash.getEvent().reply("Doing the measurement...").addCheck(() -> {
                            sendTime.set(System.currentTimeMillis());
                            return true;
                        }).flatMap(hook -> hook.editOriginal(calculateResponse(sendTime.get())))
        ).queue();
    }

    @Nonnull
    private String calculateResponse(long sendTime) {
        int ping = (int) (System.currentTimeMillis() - sendTime);
        // Idea of repeating 'o's stolen from Lewdcario's FloofBot without permission or shame
        return String.format("Po%sng! My ping is %dms.", StringUtils.repeat('o', Math.min(ping / 50, 29)), ping);
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"ping", "rtt"})
                .setShortHelp("My ping. Usage: `ping`")
                .setDetailedHelp("`ping`\n" +
                        "Gives you my ping (\"round trip time\" for Discord, basically my reaction time). The average reaction time of a human is ~250ms, let's see if I can beat that.\n" +
                        "Keep in mind that for some commands I need to crunch some numbers and even look at other web services, " +
                        "so I might take a bit longer to respond than my ping would suggest (I also have to respect Discords rate limits *yikes*).\n" +
                        "**Keep in mind that this might yield inaccurate results during leap seconds!**\n" +
                        // TODO: Look deeper into the allegations to see if linking lewds patreon is appropriate
                        "*(Idea of repeating 'o's stolen from [Lewdcario's FloofBot](https://www.patreon.com/lewdcario) with no permission and no shame (Floof's a really really good bot for the smash community and you should check it out))*\n" +
                        "Aliases: `ping`, `rtt`")
                .setCommandData(new CommandData("ping", "Displays my current ping"))
                .build();
    }
}
