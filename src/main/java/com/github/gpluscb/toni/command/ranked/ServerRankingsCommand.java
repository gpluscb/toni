package com.github.gpluscb.toni.command.ranked;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.toni_api.ToniApiClient;
import com.github.gpluscb.toni.toni_api.model.GuildUserResponse;
import com.github.gpluscb.toni.util.Pair;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ServerRankingsCommand implements Command {
    private static final Logger log = LogManager.getLogger(ServerRankingsCommand.class);

    @Nonnull
    private final ToniApiClient api;

    public ServerRankingsCommand(@Nonnull ToniApiClient api) {
        this.api = api;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        Guild guild = ctx.getEvent().getGuild();
        @SuppressWarnings("DataFlowIssue") // Command can only be in guild
        long guildId = guild.getIdLong();
        Member author = ctx.getMember();

        ctx.getEvent().deferReply().queue();
        api.guildRankings(guildId).whenComplete((guildUsers, error) -> {
            if (error != null) {
                log.catching(error);
                ctx.reply("The request to my backend service failed. I have already notified my dev, but you can shoot them a message as well if you want.").queue();
                return;
            }

            List<RestAction<Pair<GuildUserResponse, Member>>> userActions = guildUsers.stream()
                    .map(userResponse ->
                            guild.retrieveMemberById(userResponse.userId())
                                    .map(member -> new Pair<>(userResponse, member))
                    )
                    .collect(Collectors.toList());

            // FIXME: Pagination
            RestAction.allOf(userActions).queue(
                    members -> {
                        members.sort(Comparator.comparing(pair -> pair.getT().rank()));

                        List<EmbedUtil.InlineField> rankings = new ArrayList<>();

                        for (Pair<GuildUserResponse, Member> pair : members) {
                            GuildUserResponse userResponse = pair.getT();
                            Member member = pair.getU();
                            User user = member.getUser();

                            StringBuilder memberName = new StringBuilder(user.getName());
                            if (member.getNickname() != null) memberName.append(" (").append(member.getNickname()).append(')');

                            String fieldValue = String.format("%s - %s", memberName.toString(), userResponse.rating().display());

                            EmbedUtil.InlineField field = new EmbedUtil.InlineField(String.valueOf(userResponse.rank()), fieldValue);

                            rankings.add(field);
                        }

                        String rankingsString = EmbedUtil.parseInlineFields(rankings);

                        //noinspection DataFlowIssue command is in guild
                        MessageEmbed embed = EmbedUtil.getPreparedAuthor(author, author.getUser())
                                .setDescription(rankingsString)
                                .build();

                        ctx.reply(embed).queue();
                    }
            );

        });
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS})
                .setShortHelp("Displays the current rankings in this server.")
                .setDetailedHelp("Displays the current rankings in this server. Use the buttons to scroll through the pages.")
                .setCommandData(Commands.slash("rankings", "Displays server rankings").setGuildOnly(true))
                .build();
    }
}
