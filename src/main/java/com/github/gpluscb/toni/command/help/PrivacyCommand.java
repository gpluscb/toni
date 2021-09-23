package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;

public class PrivacyCommand implements Command {
    @Nonnull
    private final Message privacy;

    public PrivacyCommand(@Nonnull String supportServer, @Nonnull String twitterHandle, long devId) {
        // TODO: Update if Challonge features ever not on hold
        privacy = new MessageBuilder("Find my privacy policy at <https://gist.github.com/gpluscb/66e3318e776a900222297e698006fe5e>. If you have questions, contact my dev:\n")
                .appendFormat("• You can DM them directly if you have common servers: <@%d>%n", devId)
                .appendFormat("• You can go to my support server: %s%n", supportServer)
                .appendFormat("• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: <https://twitter.com/%s>", twitterHandle).build();
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        ctx.reply(privacy).queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"privacy"})
                .setShortHelp("Shows what data I collect and other privacy concerns. Usage: `privacy`")
                .setDetailedHelp("`privacy`\n" +
                        "Shows what data I collect and other privacy concerns as per <https://discord.com/developers/docs/legal#a-implement-good-privacy-practices>.")
                .setCommandData(new CommandData("privacy", "Shows what data I collect and other privacy concerns"))
                .build();
    }
}
