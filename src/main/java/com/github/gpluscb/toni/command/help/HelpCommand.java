package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandCategory;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import javax.annotation.Nonnull;
import java.util.List;

public class HelpCommand implements Command {
    @Nonnull
    private final List<CommandCategory> commands;

    public HelpCommand(@Nonnull List<CommandCategory> commands) {
        this.commands = commands;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        // TODO: slash command mentions
        OptionMapping commandArgMapping = ctx.getOption("command-or-category");
        String commandArg = commandArgMapping == null ? null : commandArgMapping.getAsString().toLowerCase();

        if (commandArg == null) {
            generalHelp(ctx);
            return;
        }

        EmbedBuilder builder = EmbedUtil.getPreparedAuthor(ctx.getMember(), ctx.getUser());

        CommandCategory requestedCategory = commands.stream()
                .filter(category -> commandArg.equals(category.categoryName()))
                .filter(category -> category.shortDescription() != null)
                .findAny().orElse(null);

        if (requestedCategory != null) {
            builder.setTitle(String.format("Toni's %s help", requestedCategory.categoryName()));

            String shortDescription = requestedCategory.shortDescription();
            builder.setDescription(shortDescription);

            List<EmbedUtil.InlineField> helpFields = requestedCategory.commands().stream()
                    .map(Command::getInfo)
                    .filter(info -> info.shortHelp() != null)
                    .map(info -> new EmbedUtil.InlineField(info.commandData().getName(), info.shortHelp()))
                    .toList();
            String parsedFields = EmbedUtil.parseInlineFields(helpFields);
            builder.addField("Commands", parsedFields, false);

            ctx.reply(builder.build()).queue();

            return;
        }

        CommandInfo requestedCommand = commands.stream().flatMap(category -> category.commands().stream())
                .map(Command::getInfo)
                .filter(info -> info.detailedHelp() != null)
                .filter(info -> info.commandData().getName().equals(commandArg))
                .findAny().orElse(null);

        if (requestedCommand != null) {
            String name = requestedCommand.commandData().getName();

            builder.setTitle(String.format("Toni's %s help", name))
                    .setDescription(requestedCommand.detailedHelp());

            ctx.reply(builder.build()).queue();

            return;
        }

        ctx.reply("I don't have that command or category.").queue();
    }

    private void generalHelp(@Nonnull CommandContext ctx) {
        Config config = ctx.getConfig();

        EmbedBuilder builder = EmbedUtil.getPreparedAuthor(ctx.getMember(), ctx.getUser()).setTitle("Toni's general help");

        builder.setDescription(String.format("""
                You can also see all my commands by typing `/` in the discord client.
                For more detailed info on a specific command category, you can use `/help [CATEGORY]`.
                I am still in an early state. So if you have any questions, problems, bugs, or suggestions, **please** tell my dev about that.
                • You can DM them directly if you have common servers: <@%d>
                • You can go to [my support server](%s)
                • You can @ or dm me on Twitter, I promise you only the highest quality of tweets: [@%s](https://twitter.com/%1$s)
                """, config.devId(), config.supportServer(), config.twitterHandle()));

        List<EmbedUtil.InlineField> helpFields = commands.stream()
                .filter(category -> category.categoryName() != null)
                .filter(category -> category.shortDescription() != null)
                .map(category -> new EmbedUtil.InlineField(category.categoryName(), category.shortDescription()))
                .toList();
        String parsedFields = EmbedUtil.parseInlineFields(helpFields);
        builder.addField("Command categories", parsedFields, false);

        Button githubButton = Button.link(config.github(), "Source code");
        Button inviteButton = Button.link(config.inviteUrl(), "Invite me");
        Button supportButton = Button.link(config.supportServer(), "Support server");

        MessageEmbed embed = builder.build();
        ctx.getEvent().reply(new MessageCreateBuilder().setEmbeds(embed).build())
                .addActionRow(githubButton, inviteButton, supportButton)
                .queue();
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS})
                .setShortHelp("Helps you out.")
                .setDetailedHelp("`stack owoflow - Circular reference`")
                .setCommandData(Commands.slash("help", "Helps you out")
                        .addOption(OptionType.STRING, "command-or-category", "The specific command or category name", false))
                .build();
    }
}
