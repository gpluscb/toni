package com.github.gpluscb.toni.command.help;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandCategory;
import com.github.gpluscb.toni.command.MessageCommandContext;
import com.github.gpluscb.toni.util.EmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.components.Button;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand implements Command {
    @Nonnull
    private final List<CommandCategory> commands;
    @Nonnull
    private final String supportServer;
    @Nonnull
    private final String inviteUrl;
    @Nonnull
    private final String twitterHandle;
    @Nonnull
    private final String github;
    private final long devId;
    private final long botId;

    public HelpCommand(@Nonnull List<CommandCategory> commands, @Nonnull String supportServer, @Nonnull String inviteUrl, @Nonnull String twitterHandle, @Nonnull String github, long devId, long botId) {
        this.commands = commands;
        this.supportServer = supportServer;
        this.inviteUrl = inviteUrl;
        this.twitterHandle = twitterHandle;
        this.github = github;
        this.devId = devId;
        this.botId = botId;
    }

    @Override
    public void execute(@Nonnull MessageCommandContext ctx) {
        List<String> args = ctx.getArgs();
        if (args.isEmpty()) {
            generalHelp(ctx);
            return;
        }

        String commandArg = args.get(0).toLowerCase();

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
                    .filter(command -> command.getShortHelp() != null)
                    .map(command -> new EmbedUtil.InlineField(command.getAliases()[0], command.getShortHelp()))
                    .collect(Collectors.toList());
            String parsedFields = EmbedUtil.parseInlineFields(helpFields);
            builder.addField("Commands", parsedFields, false);

            ctx.reply(builder.build()).queue();

            return;
        }

        Command requestedCommand = commands.stream().flatMap(category -> category.getCommands().stream())
                .filter(command -> Arrays.asList(command.getAliases()).contains(commandArg))
                .filter(command -> command.getDetailedHelp() != null)
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

    private void generalHelp(@Nonnull MessageCommandContext ctx) {
        EmbedBuilder builder = EmbedUtil.getPrepared(ctx.getMember(), ctx.getUser()).setTitle("Toni's general help");

        builder.setDescription("My prefixes are `!t`, `noti` and `toni`, but you can mention me instead too.\n")
                .appendDescription("`|` means \"or\", `[brackets]` mean \"optional\", and `...` means that an argument is allowed to have spaces. ")
                .appendDescription("If you want to use spaces in other arguments, you will have to wrap that argument in quotation marks (e.g. \"this is all one argument\").\n")
                .appendDescription("Use `Toni, help [CATEGORY]` for more info on specific command categories.\n")
                .appendDescription("I am still in an early state. So if you have any questions, problems, bugs, or suggestions, **please** tell my dev about that.\n")
                .appendDescription(String.format("• You can DM them directly if you have common servers: <@%d>%n", devId))
                .appendDescription(String.format("• You can go to [my support server](%s)%n", supportServer))
                .appendDescription(String.format("• You can @ or dm me on Twitter, I promise you only the highest quality of tweets: [@%s](https://twitter.com/%1$s)%n", twitterHandle))
                .appendDescription(String.format("Invite me to your server by clicking [here](%s)", inviteUrl));

        List<EmbedUtil.InlineField> helpFields = commands.stream()
                .filter(category -> category.getCategoryName() != null)
                .filter(category -> category.getShortDescription() != null)
                .map(category -> new EmbedUtil.InlineField(category.getCategoryName(), category.getShortDescription()))
                .collect(Collectors.toList());
        String parsedFields = EmbedUtil.parseInlineFields(helpFields);
        builder.addField("Command categories", parsedFields, false);

        Button topGGButton = Button.link(String.format("https://top.gg/bot/%d", botId), "Vote for me");
        Button githubButton = Button.link(github, "Source code");
        Button inviteButton = Button.link(inviteUrl, "Invite me");
        Button supportButton = Button.link(supportServer, "Support server");

        ctx.reply(builder.build())
                .setActionRow(topGGButton, githubButton, inviteButton, supportButton)
                .queue();
    }

    @Nonnull
    @Override
    public Permission[] getRequiredBotPerms() {
        return new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"help", "h"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Helps you out. Usage: `help [CATEGORY|COMMAND]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`stack owoflow - Circular reference`";
    }
}
