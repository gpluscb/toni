package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.Config;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandCategory;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand implements Command {
    @Nonnull
    private final List<CommandCategory> commands;

    public HelpCommand(@Nonnull List<CommandCategory> commands) {
        this.commands = commands;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        String commandArg = ctx.getContext().map(msg -> {
            List<String> args = msg.getArgs();
            return args.isEmpty() ? null : args.get(0).toLowerCase();
        }, slash -> {
            OptionMapping commandArgMapping = slash.getOption("command-or-category");
            return commandArgMapping == null ? null : commandArgMapping.getAsString();
        });

        if (commandArg == null) {
            generalHelp(ctx);
            return;
        }

        EmbedBuilder builder = EmbedUtil.getPrepared(ctx.getMember(), ctx.getUser());

        CommandCategory requestedCategory = commands.stream()
                .filter(category -> commandArg.equals(category.getCategoryName()))
                .filter(category -> category.getShortDescription() != null)
                .findAny().orElse(null);

        if (requestedCategory != null) {
            builder.setTitle(String.format("Toni's %s help", requestedCategory.getCategoryName()));

            String shortDescription = requestedCategory.getShortDescription();
            builder.setDescription(shortDescription);

            List<EmbedUtil.InlineField> helpFields = requestedCategory.getCommands().stream()
                    .map(Command::getInfo)
                    .filter(info -> info.getShortHelp() != null)
                    .map(info -> new EmbedUtil.InlineField(info.getAliases()[0], info.getShortHelp()))
                    .collect(Collectors.toList());
            String parsedFields = EmbedUtil.parseInlineFields(helpFields);
            builder.addField("Commands", parsedFields, false);

            ctx.reply(builder.build()).queue();

            return;
        }

        CommandInfo requestedCommand = commands.stream().flatMap(category -> category.getCommands().stream())
                .map(Command::getInfo)
                .filter(info -> Arrays.asList(info.getAliases()).contains(commandArg))
                .filter(info -> info.getDetailedHelp() != null)
                .findAny().orElse(null);

        if (requestedCommand != null) {
            String[] aliases = requestedCommand.getAliases();

            builder.setTitle(String.format("Toni's %s help", aliases[0]))
                    .setDescription(requestedCommand.getDetailedHelp());

            ctx.reply(builder.build()).queue();

            return;
        }

        ctx.reply("I don't have that command or category.").queue();
    }

    private void generalHelp(@Nonnull CommandContext<?> ctx) {
        Config config = ctx.getConfig();

        EmbedBuilder builder = EmbedUtil.getPrepared(ctx.getMember(), ctx.getUser()).setTitle("Toni's general help");

        builder.setDescription("My prefixes are `!t`, `noti` and `toni`, but you can mention me instead too.\n")
                .appendDescription("`|` means \"or\", `[brackets]` mean \"optional\", and `...` means that an argument is allowed to have spaces. ")
                .appendDescription("If you want to use spaces in other arguments, you will have to wrap that argument in quotation marks (e.g. \"this is all one argument\").\n")
                .appendDescription("Use `Toni, help [CATEGORY]` for more info on specific command categories.\n")
                .appendDescription("I am still in an early state. So if you have any questions, problems, bugs, or suggestions, **please** tell my dev about that.\n")
                .appendDescription(String.format("• You can DM them directly if you have common servers: <@%d>%n", config.getDevId()))
                .appendDescription(String.format("• You can go to [my support server](%s)%n", config.getSupportServer()))
                .appendDescription(String.format("• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: [@%s](https://twitter.com/%1$s)%n", config.getTwitterHandle()))
                .appendDescription(String.format("Invite me to your server by clicking [here](%s)", config.getInviteUrl()));

        List<EmbedUtil.InlineField> helpFields = commands.stream()
                .filter(category -> category.getCategoryName() != null)
                .filter(category -> category.getShortDescription() != null)
                .map(category -> new EmbedUtil.InlineField(category.getCategoryName(), category.getShortDescription()))
                .collect(Collectors.toList());
        String parsedFields = EmbedUtil.parseInlineFields(helpFields);
        builder.addField("Command categories", parsedFields, false);

        Button topGGButton = Button.link(String.format("https://top.gg/bot/%d", config.getBotId()), "Vote for me");
        Button githubButton = Button.link(config.getGithub(), "Source code");
        Button inviteButton = Button.link(config.getInviteUrl(), "Invite me");
        Button supportButton = Button.link(config.getSupportServer(), "Support server");

        MessageEmbed embed = builder.build();
        ctx.getContext().onT(msg ->
                        msg.reply(embed)
                                .setActionRow(topGGButton, githubButton, inviteButton, supportButton)
                                .queue())
                .onU(slash ->
                        slash.getEvent().reply(new MessageBuilder().setEmbeds(embed).build())
                                .addActionRow(topGGButton, githubButton, inviteButton, supportButton)
                                .queue());
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS})
                .setAliases(new String[]{"help", "h"})
                .setShortHelp("Helps you out. Usage: `help [CATEGORY|COMMAND]`")
                .setDetailedHelp("`stack owoflow - Circular reference`")
                .setCommandData(new CommandData("help", "Helps you out")
                        .addOption(OptionType.STRING, "command-or-category", "The specific command or category name", false))
                .build();
    }
}
