package com.github.gpluscb.toni.command.matchmaking;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.matchmaking.UnrankedManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;

public class UnrankedConfigCommand implements Command {
    private static final Logger log = LogManager.getLogger(UnrankedConfigCommand.class);

    @Nonnull
    private final UnrankedManager manager;

    public UnrankedConfigCommand(@Nonnull UnrankedManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        // TODO: I feel like this is still bad ux, maybe there should be a setup variant but like this it's unintuitive
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("This command only works in servers, I won't set up matchmaking in our DMs.").queue();
            return;
        }

        // We know the member is not null because we're in a guild
        Member member = ctx.getMember();
        // TODO: Is this too restrictive?
        //noinspection ConstantConditions
        if (!(member.hasPermission(ctx.getEvent().getTextChannel(), Permission.MANAGE_CHANNEL) && member.hasPermission(Permission.MANAGE_ROLES))) {
            ctx.reply("I don't trust you... you need to have both Manage Channel and Manage Roles permission to use this.").queue();
            return;
        }

        String variant = ctx.getArg(0).toLowerCase();

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

        // Default variant
        int argNum = ctx.getArgNum();
        if (argNum < 1) {
            ctx.reply("Too few arguments. Correct usage is `unrankedcfg <role mention> [channel mention]`.").queue();
            return;
        }

        Role role = ctx.getRoleMentionArg(0);
        if (role == null) {
            ctx.reply("The first argument must be a mention of a role in this server.").queue();
            return;
        }

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        Long channelId = null;
        if (argNum > 1) {
            TextChannel channel = ctx.getChannelMentionArg(1);
            if (channel == null) {
                ctx.reply("The second argument must be a mention of a channel in this server." +
                        " If you don't want matchmaking to be restricted to a channel, just leave that blank.").queue();
                return;
            }

            channelId = channel.getIdLong();
        }

        UnrankedManager.MatchmakingConfig config = new UnrankedManager.MatchmakingConfig(roleId, channelId);

        try {
            boolean wasStored = manager.storeMatchmakingConfig(ctx.getEvent().getGuild().getIdLong(), config);

            String message = wasStored ? "Success! You are now set up with matchmaking."
                    : "Matchmaking was already set up." +
                    " If you want to change the role or channel, use `toni, unrankedcfg role <role>` and `toni, unrankedcfg channel <channel|all>`.";

            ctx.reply(message).queue();
        } catch (SQLException e) {
            log.catching(e);
            ctx.reply("Oh no! Something went wrong talking to the database." +
                    " I've told my dev about this, if this keeps happening you should give them some context too.").queue();
        }
    }

    private void channelVariant(@Nonnull CommandContext ctx) {
        if (ctx.getArgNum() < 2) {
            ctx.reply("Too few arguments. I need to know what the matchmaking role is!").queue();
            return;
        }

        Long channelId;
        if (ctx.getArg(1).equalsIgnoreCase("all")) {
            channelId = null;
        } else {
            TextChannel channel = ctx.getChannelMentionArg(1);
            if (channel == null) {
                ctx.reply("The first argument for the channel variant must be a channel mention.").queue();
                return;
            }

            channelId = channel.getIdLong();
        }

        long guildId = ctx.getEvent().getGuild().getIdLong();

        try {
            boolean wasPresent = manager.updateMatchmakingChannel(guildId, channelId);

            if (wasPresent) {
                ctx.reply("How wonderful! The configuration was changed successfully!").queue();
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

    private void roleVariant(@Nonnull CommandContext ctx) {
        if (ctx.getArgNum() < 2) {
            ctx.reply("Too few arguments. I need to know what the matchmaking role is!").queue();
            return;
        }

        Role role = ctx.getRoleMentionArg(1);
        if (role == null) {
            ctx.reply("When using the role update variant, the first argument has to be a role mention.").queue();
            return;
        }

        if (!checkRole(ctx, role)) return;

        long roleId = role.getIdLong();

        long guildId = ctx.getEvent().getGuild().getIdLong();

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

    private void resetVariant(@Nonnull CommandContext ctx) {
        try {
            boolean wasPresent = manager.deleteMatchmakingConfig(ctx.getEvent().getGuild().getIdLong());

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
    public String[] getAliases() {
        return new String[]{"unrankedconfig", "unrankedcfg"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Helps you configure unranked matchmaking. Usage: `unrankedconfig|unrankedcfg <OPTIONS>`. For more info, see `toni, help unrankedcfg`.";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`unrankedconfig|unrankedcfg <ROLE> [CHANNEL]` Sets up matchmaking with the specified matchmaking role, optionally only in a specific channel.\n" +
                "`unrankedconfig|unrankedcfg channel <CHANNEL|\"ALL\">`" +
                " Sets a specific channel for the matchmaking configuration, or removes channel restrictions if the argument is `all`\n" +
                "`unrankedconfig|unrankedcfg role <ROLE>` Sets a matchmaking role.\n" +
                "`unrankedconfig|unrankedcfg reset` Removes matchmaking from this server.";
    }
}
