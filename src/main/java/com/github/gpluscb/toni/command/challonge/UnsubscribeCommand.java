package com.github.gpluscb.toni.command.challonge;

import com.github.gpluscb.challonge_listener.ChallongeExtension;
import com.github.gpluscb.toni.challonge.TournamentListener;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.util.FailLogger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnsubscribeCommand implements Command {
    private final static Logger log = LogManager.getLogger(UnsubscribeCommand.class);

    @Nonnull
    private final ChallongeExtension challonge;
    @Nonnull
    private final TournamentListener listener;

    public UnsubscribeCommand(@Nonnull ChallongeExtension challonge, @Nonnull TournamentListener listener) {
        this.challonge = challonge;
        this.listener = listener;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        if (!ctx.getEvent().isFromGuild()) {
            ctx.reply("Hey, this command only works in servers!").queue();
            return;
        }

        if (!ctx.hasManageChannelsPermission() && !ctx.hasAdminPermission()) {
            ctx.reply("Hmmm, can I really really trust you? You'll need the `Manage Channels` permission to use this.").queue();
            return;
        }

        Guild guild = ctx.getEvent().getGuild();

        if (ctx.getArgs().isEmpty()) {
            // Potentially multiple subs per guild
            List<TournamentListener.Subscription> subs = filterForGuild(guild, listener.getSubscriptions().stream()).collect(Collectors.toList());
            if (subs.isEmpty()) {
                ctx.reply("I don't think this server is subscribed to any tournaments. If you think this is a bug, please tell my dev. They'll be happy to hear about it.").queue();
                return;
            }

            if (subs.size() > 1) {
                ctx.reply("There are multiple subscriptions linked to this server. Please specify which one you mean by telling me either the channel the subscription is in, or the tournament url of the tournament in question.").queue();
                return;
            }

            sendResponse(ctx, subs.get(0));
            return;
        }

        TextChannel channel = ctx.getChannelMentionArg(0);
        if (channel != null) {
            long channelId = channel.getIdLong();

            TournamentListener.Subscription sub = filterForGuild(guild, listener.findSubscriptionsByChannelId(channelId).stream()).findAny().orElse(null);
            if (sub == null) {
                ctx.reply("I don't think there's a subscription in that channel... If you think this is a bug, please contact my dev. They'll be happy to fix that.").queue();
                return;
            }

            sendResponse(ctx, sub);
            return;
        }

        String arg = ctx.getArgsFrom(0);
        challonge.getTournamentOrNull(arg, FailLogger.logFail(tournament -> {
            if (tournament == null) {
                ctx.reply("I didn't find any tournament for that url. The part of the url I'd need would, for example, be `example` for `challonge.com/example`.").queue();
                return;
            }

            TournamentListener.Subscription sub = filterForGuild(guild, listener.findSubscriptionsByTournamentId(tournament.getId()).stream()).findAny().orElse(null);
            if (sub == null) {
                ctx.reply("I didn't find a subscription for that tournament in here. If you think this is a bug, please tell my dev. They'll be happy to fix that.").queue();
                return;
            }

            sendResponse(ctx, sub);
        }), FailLogger.logFail(failure -> {
            ctx.reply("The request to challonge failed. If this keeps happening please tell my dev about what you're doing. I've already sent a small report, but it can't hurt to give them more context.").queue();
            log.catching(failure);
        }));
    }

    @Nonnull
    private Stream<TournamentListener.Subscription> filterForGuild(@Nonnull Guild guild, @Nonnull Stream<TournamentListener.Subscription> subs) {
        return subs.filter(sub -> guild.getTextChannelById(sub.getLogChannelId()) != null);
    }

    private void sendResponse(@Nonnull CommandContext ctx, @Nonnull TournamentListener.Subscription sub) {
        try {
            listener.unsubscribe(sub);

            ctx.reply("Success! You should stop getting logs now.").queue();
        } catch (SQLException e) {
            ctx.reply("Looks like my database is failing, so I can't unsubscribe you right now... I'll tell my dev about that, but you can give them some context too. They'll probably have to do some stuff manually.").queue();
            log.error("DB: failure to unsubscribe", e);
        }
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"unsubscribe", "unsub", "unlink"};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Reverses a subscription of a channel in this server to a tournament on challonge. Usage: `unsubscribe [END OF TOURNAMENT URL | CHANNEL MENTION]`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`unsub[scribe]|unlink [END OF TOURNAMENT URL | CHANNEL MENTION]`\nReverses a subscription to a tournament. The tournament url/channel mention is not needed if only one subscription exists in the server.";
    }
}
