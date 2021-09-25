package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import com.github.gpluscb.toni.util.OneOfTwo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;

public class UnrankedConfigCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedConfigCommand.class);

    @Nonnull
    private final String supportServer;

    @Nonnull
    private final UnrankedManager manager;

    public UnrankedConfigCommand(@Nonnull String supportServer, @Nonnull UnrankedManager manager) {
        this.supportServer = supportServer;
        this.manager = manager;
    }

    @Override
    public void execute(@Nonnull CommandContext<?> ctx) {
        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        if (!context.map(msg -> msg.getEvent().isFromGuild(), slash -> slash.getEvent().isFromGuild())) {
            ctx.reply("This command only works in servers, I won't set up matchmaking in our DMs.").queue();
            return;
        }

        if (context.isT() && context.getTOrThrow().getArgNum() <= 0) {
            ctx.reply("Too few arguments. For help, check out `toni, help unrankedcfg`.").queue();
            return;
        }

        Member member = ctx.getMember();
        TextChannel textChannel = context.map(msg -> msg.getEvent().getTextChannel(), slash -> slash.getEvent().getTextChannel());
        // TODO: Is this too restrictive?
        // We know the member is not null because we're in a guild
        //noinspection ConstantConditions
        if (!(member.hasPermission(textChannel, Permission.MANAGE_CHANNEL) && member.hasPermission(Permission.MANAGE_ROLES))) {
            ctx.reply("I don't trust you... you need to have both the Manage Channel and Manage Roles permission to use this.").queue();
            return;
        }

        String variant = context.map(msg -> msg.getArg(0).toLowerCase(), slash -> slash.getEvent().getSubcommandName());

        switch (variant) {
            case "channel":
                channelVariant(ctx);
                return;
            case "role":
                roleVariant(ctx);
                return;
            case "reset":
                resetVariant(ctx);
                return;
        }

        // Default variant / Setup variant
        if (context.isT() && context.getTOrThrow().getArgNum() < 1) {
            ctx.reply("Too few arguments. Correct usage is `unrankedcfg <role mention> [channel mention]`.").queue();
            return;
        }

        Role role;
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();
            role = msg.getRoleMentionArg(0);
            if (role == null) {
                ctx.reply("The first argument must be a mention of a role in this server.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();
            role = slash.getOptionNonNull("role").getAsRole();
        }

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        Long channelId = null;
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();
            if (msg.getArgNum() > 1) {
                TextChannel channel = msg.getChannelMentionArg(1);
                if (channel == null) {
                    ctx.reply("The second argument must be a mention of a channel in this server." +
                            " If you don't want matchmaking to be restricted to a channel, just leave that blank.").queue();
                    return;
                }

                channelId = channel.getIdLong();
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();
            OptionMapping channelMapping = slash.getOption("channel");
            if (channelMapping != null) {
                GuildChannel guildChannel = channelMapping.getAsGuildChannel();
                if (!(guildChannel instanceof TextChannel)) {
                    ctx.reply("The channel must be a *text* channel.").queue();
                    return;
                }

                channelId = channelMapping.getAsGuildChannel().getIdLong();
            }
        }

        UnrankedManager.MatchmakingConfig config = new UnrankedManager.MatchmakingConfig(roleId, channelId);

        try {
            // TODO: You know maybe it would be nice to have an upsert here
            long guildId = context.map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild()).getIdLong();
            boolean wasStored = manager.storeMatchmakingConfig(guildId, config);

            if (wasStored) {
                ctx.reply("Success! You are now set up with matchmaking.").queue();
                return;
            }

            // Matchmaking already existed here
            boolean wasUpdated = manager.updateMatchmakingConfig(guildId, config);
            if (wasUpdated) {
                ctx.reply("I have now changed the matchmaking configuration.").queue();
                return;
            }

            log.warn("Configuration was not stored and later was not updated. Guild: {}", guildId);
            ctx.reply("This is a bit weird, it looks like matchmaking was already set up, but wasn't any more when I looked a few milliseconds later..." +
                    " If this doesn't fix itself when you use the command again, please tell my dev. I've already told them about it," +
                    " but you can give them some context too.").queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Oh no! Something went wrong talking to the database." +
                    " I've told my dev about this, if this keeps happening you should give them some context too.").queue();
        }

    }

    private void channelVariant(@Nonnull CommandContext<?> ctx) {
        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        if (context.isT() && context.getTOrThrow().getArgNum() < 2) {
            ctx.reply("Too few arguments. I need to know what the matchmaking role is!").queue();
            return;
        }

        Long channelId;

        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            if (msg.getArg(1).equalsIgnoreCase("all")) {
                channelId = null;
            } else {
                TextChannel channel = msg.getChannelMentionArg(1);
                if (channel == null) {
                    ctx.reply("The first argument for the channel variant must be a channel mention.").queue();
                    return;
                }

                channelId = channel.getIdLong();
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            GuildChannel channel = slash.getOptionNonNull("channel").getAsGuildChannel();
            if (!(channel instanceof TextChannel)) {
                ctx.reply("The channel must be a *text* channel.").queue();
                return;
            }

            channelId = channel.getIdLong();
        }

        long guildId = context.map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild()).getIdLong();

        try {
            boolean wasPresent = manager.updateMatchmakingChannel(guildId, channelId);

            if (wasPresent) {
                ctx.reply("Congrats! The configuration was changed successfully!").queue();
                return;
            }

            ctx.reply("I can't set up a matchmaking channel if there is no matchmaking role already." +
                    " Use `toni, unrankedcfg <role mention> [channel mention]` to do both").queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went horribly wrong trying to talk to my database!" +
                    " Try again later, and if this keeps happening tell my dev. I've already told them about it, but it'll help if you can talk to them.").queue();
        }
    }

    private void roleVariant(@Nonnull CommandContext<?> ctx) {
        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();

        if (context.isT() && context.getTOrThrow().getArgNum() < 2) {
            ctx.reply("Too few arguments. I need to know what the matchmaking role is!").queue();
            return;
        }

        Role role;
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            role = msg.getRoleMentionArg(1);
            if (role == null) {
                ctx.reply("When using the role update variant, the first argument has to be a role mention.").queue();
                return;
            }
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            role = slash.getOptionNonNull("role").getAsRole();
        }

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        long guildId = context.map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild()).getIdLong();

        try {
            boolean wasPresent = manager.updateMatchmakingRole(guildId, roleId);

            if (wasPresent) {
                ctx.reply("Yay! The changes were made successfully!").queue();
                return;
            }

            // If not present, create:
            manager.storeMatchmakingConfig(guildId, new UnrankedManager.MatchmakingConfig(roleId, null));

            ctx.reply("I have now set up unranked matchmaking with the given role." +
                    " If you want to restrict the matchmaking to a specific channel, use `toni, matchmakingcfg channel [channel mention]`").queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went horribly wrong trying to talk to my database!" +
                    " Try again later, and if this keeps happening tell my dev. I've already told them about it, but it'll help if you can talk to them.").queue();
        }
    }

    private void resetVariant(@Nonnull CommandContext<?> ctx) {
        long guildId = ctx.getContext().map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild()).getIdLong();

        try {
            boolean wasPresent = manager.deleteMatchmakingConfig(guildId);

            String response = wasPresent ? "Ok I successfully deleted the configuration now."
                    : "There was no matchmaking configuration in the first place, what are you resetting it for?";

            ctx.reply(response).queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went horribly wrong trying to talk to my database!" +
                    " Try again later, and if this keeps happening tell my dev. I've already told them about it, but it'll help if you can talk to them.").queue();
        }
    }

    // I don't like this warning I feel like it makes stuff less intuitive sometimes
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkRole(@Nonnull CommandContext<?> ctx, @Nonnull Role role) {
        if (!(role.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MENTION_EVERYONE) || role.isMentionable())) {
            ctx.reply("The role you provided is not mentionable, but I need to be able to ping it.").queue();
            return false;
        }

        Guild guild = ctx.getContext().map(msg -> msg.getEvent().getGuild(), slash -> slash.getEvent().getGuild());

        Member selfMember = guild.getSelfMember();
        if (!(selfMember.canInteract(role) && selfMember.hasPermission(Permission.MANAGE_ROLES))) {
            ctx.reply("I won't be able to assign this role to people." +
                    " Please make sure I have permission to manage roles, and that the role is lower than my highest role.").queue();
            return false;
        }

        return true;
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setAliases(new String[]{"unrankedconfig", "unrankedcfg"})
                .setShortHelp("Helps you configure unranked matchmaking. For more info on usage, see `toni, help unrankedconfig`.")
                .setDetailedHelp("`unrankedconfig <ROLE> [CHANNEL]` Sets up matchmaking with the specified matchmaking role, optionally only in a specific channel.\n" +
                        "`unrankedconfig channel <CHANNEL|\"ALL\">`" +
                        " Sets a specific channel for the matchmaking configuration, or removes channel restrictions if the argument is `all`.\n" +
                        "`unrankedconfig role <ROLE>` Sets a matchmaking role.\n" +
                        "`unrankedconfig reset` Removes matchmaking from this server.\n" +
                        "Aliases: `unrankedconfig`, `unrankedcfg`")
                .setCommandData(new CommandData("unrankedconfig", "Configuration for unranked matchmaking")
                        .addSubcommands(new SubcommandData("channel", "Update the matchmaking channel. Resets the channel to none if no channel is given")
                                        .addOption(OptionType.CHANNEL, "channel", "The new matchmaking channel", false),
                                new SubcommandData("role", "Update the matchmaking role")
                                        .addOption(OptionType.ROLE, "role", "The new matchmaking role", true),
                                new SubcommandData("reset", "Removes unranked matchmaking from this server"),
                                new SubcommandData("setup", "Set up unranked matchmaking for this server")
                                        .addOption(OptionType.ROLE, "role", "The matchmaking role", true)
                                        .addOption(OptionType.CHANNEL, "channel", "The matchmaking channel if you want to limit unranked matchmaking to one channel", false))
                ).build();
    }
}
