package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.MessageCommandContext;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

public class PingCommand implements Command {
    @Override
    public void execute(@Nonnull MessageCommandContext ctx) {
        AtomicLong sendTime = new AtomicLong();
        ctx.reply("Doing the measurement...").addCheck(() -> {
            sendTime.set(System.currentTimeMillis());
            return true;
        }).flatMap(m -> {
            int ping = (int) (System.currentTimeMillis() - sendTime.get());
            // Idea of repeating 'o's stolen from Lewdcario's FloofBot without permission or shame
            return m.editMessage(String.format("Po%sng! My ping is %dms.", StringUtils.repeat('o', Math.min(ping / 50, 29)), ping));
        }).queue();
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"ping", "rtt"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "My ping. Usage: `ping`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`ping`\n" +
                "Gives you my ping (\"round trip time\" for Discord, basically my reaction time). The average reaction time of a human is ~250ms, let's see if I can beat that.\n" +
                "Keep in mind that for some commands I need to crunch some numbers and even look at other web services, " +
                "so I might take a bit longer to respond than my ping would suggest (I also have to respect Discords rate limits *yikes*).\n" +
                "**Keep in mind that this might yield inaccurate results during leap seconds!**\n" +
                "*(Idea of repeating 'o's stolen from [Lewdcario's FloofBot](https://www.patreon.com/lewdcario) with no permission and no shame (Floof's a really really good bot for the smash community and you should check it out))*\n" +
                "Aliases: `ping`, `rtt`";
    }
}
