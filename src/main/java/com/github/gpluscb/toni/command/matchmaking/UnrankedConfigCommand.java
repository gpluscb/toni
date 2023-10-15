package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.db.DBManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;

public class UnrankedConfigCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedConfigCommand.class);

    @Nonnull
    private final DBManager manager;

    public UnrankedConfigCommand(@Nonnull DBManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers, I won't set up matchmaking in our DMs.").queue();
            return;
        }

        Member member = ctx.getMember();
        GuildMessageChannel messageChannel = ctx.getEvent().getGuildChannel();
        // TODO: Is this too restrictive?
        // We know the member is not null because we're in a guild
        //noinspection ConstantConditions
        if (!(member.hasPermission(messageChannel, Permission.MANAGE_CHANNEL) && member.hasPermission(Permission.MANAGE_ROLES))) {
            ctx.reply("I don't trust you... you need to have both the Manage Channel and Manage Roles permission to use this.").queue();
            return;
        }

        String variant = ctx.getEvent().getSubcommandName();

        // We know this is a subcommand
        //noinspection ConstantConditions
        switch (variant) {
            case "channel" -> {
                channelVariant(ctx);
                return;
            }
            case "role" -> {
                roleVariant(ctx);
                return;
            }
            case "reset" -> {
                resetVariant(ctx);
                return;
            }
        }

        Role role = ctx.getOptionNonNull("role").getAsRole();

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        Long channelId = null;

        OptionMapping channelMapping = ctx.getOption("channel");
        if (channelMapping != null) {
            GuildChannelUnion guildChannel = channelMapping.getAsChannel();
            if (guildChannel.getType() != ChannelType.TEXT) {
                ctx.reply("The channel must be a *text* channel.").queue();
                return;
            }

            channelId = channelMapping.getAsChannel().getIdLong();
        }

        DBManager.MatchmakingConfig config = new DBManager.MatchmakingConfig(roleId, channelId);

        try {
            // TODO: You know maybe it would be nice to have an upsert here
            @SuppressWarnings("ConstantConditions") // We already checked for isFromGuild
            long guildId = ctx.getEvent().getGuild().getIdLong();
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

    private void channelVariant(@Nonnull CommandContext ctx) {
        GuildChannelUnion channel = ctx.getOptionNonNull("channel").getAsChannel();
        if (channel.getType() != ChannelType.TEXT) {
            ctx.reply("The channel must be a *text* channel.").queue();
            return;
        }

        Long channelId = channel.getIdLong();

        @SuppressWarnings("ConstantConditions") // Already checked for isFromGuild
        long guildId = ctx.getEvent().getGuild().getIdLong();

        try {
            boolean wasPresent = manager.updateMatchmakingChannel(guildId, channelId);

            if (wasPresent) {
                ctx.reply("Congrats! The configuration was changed successfully!").queue();
                return;
            }

            ctx.reply("I can't set up a matchmaking channel if there is no matchmaking role already." +
                    " Use `/unrankedconfig <role mention> [channel mention]` to do both").queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went horribly wrong trying to talk to my database!" +
                    " Try again later, and if this keeps happening tell my dev. I've already told them about it, but it'll help if you can talk to them.").queue();
        }
    }

    private void roleVariant(@Nonnull CommandContext ctx) {
        Role role = ctx.getOptionNonNull("role").getAsRole();

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        @SuppressWarnings("ConstantConditions") // Already checked for isFromGuild
        long guildId = ctx.getEvent().getGuild().getIdLong();

        try {
            boolean wasPresent = manager.updateMatchmakingRole(guildId, roleId);

            if (wasPresent) {
                ctx.reply("Yay! The changes were made successfully!").queue();
                return;
            }

            // If not present, create:
            manager.storeMatchmakingConfig(guildId, new DBManager.MatchmakingConfig(roleId, null));

            ctx.reply("I have now set up unranked matchmaking with the given role." +
                    " If you want to restrict the matchmaking to a specific channel, use `/unrankedconfig channel [channel mention]`").queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Something went horribly wrong trying to talk to my database!" +
                    " Try again later, and if this keeps happening tell my dev. I've already told them about it, but it'll help if you can talk to them.").queue();
        }
    }

    private void resetVariant(@Nonnull CommandContext ctx) {
        @SuppressWarnings("ConstantConditions") // Already checked for isFromGuild
        long guildId = ctx.getEvent().getGuild().getIdLong();

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
    private boolean checkRole(@Nonnull CommandContext ctx, @Nonnull Role role) {
        if (!(role.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MENTION_EVERYONE) || role.isMentionable())) {
            ctx.reply("The role you provided is not mentionable, but I need to be able to ping it.").queue();
            return false;
        }

        Guild guild = ctx.getEvent().getGuild();

        @SuppressWarnings("ConstantConditions") // Already checked for isFromGuild
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
                .setShortHelp("Helps you configure unranked matchmaking. For more info on usage, see `/help unrankedconfig`.")
                .setDetailedHelp("""
                        Configures unranked matchmaking in this server. You need to have the Manage Server and Manage Roles permissions to use this command.
                        `/unrankedconfig setup`: Set up unranked matchmaking for this server.
                        Slash command options:
                        • `role`: The matchmaking role.
                        • (Optional) `channel`: The matchmaking channel. By default, using matchmaking commands will be possible in all channels.
                        `/unrankedconfig role`: Updates the matchmaking role.
                        Slash command options:
                        • `role`: The new matchmaking role.
                        `/unrankedconfig channel` Update the matchmaking channel.
                        Slash command options:
                        • (Optional) `channel`: The new matchmaking channel. If no channel is given, using matchmaking commands will be possible in all channels.
                        `/unrankedconfig reset` Removes unranked matchmaking from this server.""")
                .setCommandData(Commands.slash("unrankedconfig", "Configuration for unranked matchmaking")
                        .addSubcommands(new SubcommandData("channel", "Update the matchmaking channel. Resets the channel to none if no channel is given")
                                        .addOption(OptionType.CHANNEL, "channel", "The new matchmaking channel", true),
                                new SubcommandData("role", "Update the matchmaking role")
                                        .addOption(OptionType.ROLE, "role", "The new matchmaking role", true),
                                new SubcommandData("reset", "Removes unranked matchmaking from this server"),
                                new SubcommandData("setup", "Set up unranked matchmaking for this server")
                                        .addOption(OptionType.ROLE, "role", "The matchmaking role", true)
                                        .addOption(OptionType.CHANNEL, "channel", "The matchmaking channel if you want to limit unranked matchmaking to one channel", false))
                ).build();
    }
}
