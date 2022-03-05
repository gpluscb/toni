package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import javax.annotation.Nonnull;

public class TermsCommand implements Command {
    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        Config config = ctx.getConfig();

        // TODO: Update if Challonge features ever not on hold
        Message privacy = new MessageBuilder("Find my Terms of Service at <https://gist.github.com/gpluscb/4e80b3c3f90ee02f8b539bdf5f63c242>, and my Privacy Policy at <https://gist.github.com/gpluscb/66e3318e776a900222297e698006fe5e>. If you have questions, contact my dev:\n")
                .appendFormat("• You can DM them directly if you have common servers: <@%d>%n", config.devId())
                .appendFormat("• You can go to my support server: %s%n", config.supportServer())
                .appendFormat("• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: <https://twitter.com/%s>", config.getTwitterHandle()).build();

        ctx.reply(privacy).queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"terms", "privacy", "tos"})
                .setShortHelp("Shows my Terms of Service and Privacy Policy. Usage: `terms`")
                .setDetailedHelp("`terms`\n" +
                        "Shows my Terms of Service and Privacy Policy. The Privacy Policy shows what data I collect and other privacy concerns as per <https://discord.com/developers/docs/legal#a-implement-good-privacy-practices>.")
                .setCommandData(new CommandData("terms", "Shows my Terms of Service and Privacy Policy"))
                .build();
    }
}
