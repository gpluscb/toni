package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.MessageCommandContext;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.discord.ReactionActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SmashdataCommand implements Command {
    private static final Logger log = LogManager.getLogger(SmashdataCommand.class);

    @Nonnull
    private final SmashdataManager smashdata;
    @Nonnull
    private final EventWaiter waiter;

    public SmashdataCommand(@Nonnull EventWaiter waiter, @Nonnull SmashdataManager smashdata) {
        this.waiter = waiter;
        this.smashdata = smashdata;
    }

    @Override
    public void execute(@Nonnull MessageCommandContext ctx) {
        if (ctx.getArgs().isEmpty()) {
            ctx.reply("Too few arguments. I can't just find you a player if I don't know what you're searching for. Use `help player` for help.").queue();
            return;
        }

        String requestedTag = ctx.getArgsFrom(0).toUpperCase();

        ctx.getChannel().sendTyping().queue();

        try {
            List<SmashdataManager.PlayerData> results = smashdata.loadSmashdataByTag(requestedTag);

            Comparator<SmashdataManager.PlayerData> comp = (a, b) -> {
                // Find the higher ranked player
                Integer aPgru = a.getPgru();
                Integer bPgru = b.getPgru();
                if (aPgru != null) {
                    if (bPgru == null) return 1;
                    else return -Integer.compare(aPgru, bPgru);
                } else if (bPgru != null) {
                    return -1;
                }

                // Find player with socials
                int aSocial = a.getSocial().getTwitter().size();
                int bSocial = b.getSocial().getTwitter().size();
                if (aSocial != bSocial) return Integer.compare(aSocial, bSocial);

                // Find player with higher character count
                int aCount = a.getCharacters().values().stream().mapToInt(c -> c).sum();
                int bCount = b.getCharacters().values().stream().mapToInt(c -> c).sum();
                if (aCount != bCount) return Integer.compare(aCount, bCount);

                int aTotalData = 0;
                if (a.getState() != null && !a.getState().isEmpty()) aTotalData++;
                if (a.getCountry() != null && !a.getCountry().isEmpty()) aTotalData++;
                if (!a.getPrefixes().isEmpty()) aTotalData++;
                if (a.getRegion() != null && !a.getRegion().isEmpty()) aTotalData++;

                int bTotalData = 0;
                if (b.getState() != null && !b.getState().isEmpty()) bTotalData++;
                if (b.getCountry() != null && !b.getCountry().isEmpty()) bTotalData++;
                if (!b.getPrefixes().isEmpty()) bTotalData++;
                if (b.getRegion() != null && !b.getRegion().isEmpty()) bTotalData++;

                return Integer.compare(aTotalData, bTotalData);
            };

            results.sort(comp.reversed());

            sendReply(ctx, results);
        } catch (SQLException e) {
            ctx.reply("Something with the database for the players is broken :( I will notify my dev, but you can give them some context too if you want to.").queue();
            log.catching(e);
        }
    }

    private void sendReply(@Nonnull MessageCommandContext ctx, @Nonnull List<SmashdataManager.PlayerData> results) {
        if (results.isEmpty()) {
            ctx.reply("I couldn't find anything for that input, sorry.").queue();
            return;
        }

        User author = ctx.getUser();
        Member member = ctx.getEvent().getMember();

        PlayerEmbedPaginator pages = new PlayerEmbedPaginator(EmbedUtil.getPreparedSmashdata(member, author).build(), results);
        ReactionActionMenu.Builder menuBuilder = new ReactionActionMenu.Builder()
                .setEventWaiter(waiter)
                .addUsers(author.getIdLong())
                .setStart(pages.getCurrent());

        if (results.size() > 1) {
            menuBuilder.registerButton(Constants.ARROW_BACKWARD, pages::prevResult)
                    .registerButton(Constants.ARROW_FORWARD, pages::nextResult);
        }

        menuBuilder.build().displayReplying(ctx.getMessage());
    }

    @Nonnull
    private EmbedBuilder applyData(@Nonnull EmbedBuilder builder, @Nonnull List<SmashdataManager.PlayerData> players, int idx) {
        SmashdataManager.PlayerData data = players.get(idx);
        String title = players.size() > 1 ?
                String.format("(%d/%d) Smasher: %s", idx + 1, players.size(), data.getTag())
                : String.format("Smasher: %s", data.getTag());

        builder.setTitle(title);

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        List<String> prefixes = data.getPrefixes();
        if (!prefixes.isEmpty())
            fields.add(new EmbedUtil.InlineField("Prefixes", String.join(", ", prefixes)));

        String country = data.getCountry();
        if (country != null && !country.isEmpty()) {
            fields.add(new EmbedUtil.InlineField("Country", country));

            String state = data.getState();
            if (state != null && !state.isEmpty())
                fields.add(new EmbedUtil.InlineField("State", state));

            String region = data.getRegion();
            if (region != null && !region.isEmpty())
                fields.add(new EmbedUtil.InlineField("Region", region));
        }

        Map<String, Integer> characters = data.getCharacters();
        if (!characters.isEmpty()) {
            // Filter for the ones they play more than 5% of the time
            int total = characters.values().stream().mapToInt(it -> it).sum();

            String mostPlayedChars = characters.entrySet().stream().filter(entry -> entry.getValue() / (double) total > .05)
                    .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                    .map(entry -> String.format("%s (%d)", MiscUtil.capitalizeFirst(entry.getKey().replace("ultimate/", "")), entry.getValue())).collect(Collectors.joining(", "));

            fields.add(new EmbedUtil.InlineField("Characters", mostPlayedChars));
        }

        Integer ranking = data.getPgru();
        if (ranking != null)
            fields.add(new EmbedUtil.InlineField("PGRU Season 2", ranking.toString()));

        List<String> twitter = data.getSocial().getTwitter();
        if (!twitter.isEmpty()) {
            String twitters = twitter.stream().filter(Predicate.not(String::isEmpty)).map(it -> String.format("[@%s](https://twitter.com/%1$s)", it)).collect(Collectors.joining(", "));
            if (!twitters.isEmpty())
                fields.add(new EmbedUtil.InlineField("Social", String.format("Twitter: %s", twitters)));
        }

        builder.setDescription(EmbedUtil.parseInlineFields(fields));

        return builder;
    }

    @Nonnull
    @Override
    public String[] getAliases() {
        return new String[]{"smashdata", "player", "smasher", "data"};
    }

    @Nonnull
    @Override
    public Permission[] getRequiredBotPerms() {
        return new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Nullable
    @Override
    public String getShortHelp() {
        return "Displays info about a smasher using data from [smashdata.gg](https://smashdata.gg). Usage: `smashdata <TAG...>`";
    }

    @Nullable
    @Override
    public String getDetailedHelp() {
        return "`smashdata <TAG...>`\n" +
                "Looks up a smash ultimate player by their tag using data from [smashdata.gg](https://smashdata.gg).\n" +
                String.format("If there are multiple smashers matching the given tag, use the %s/%s to cycle through them.%n", Constants.ARROW_BACKWARD, Constants.ARROW_FORWARD) +
                "Aliases: `smashdata`, `player`, `smasher`, `data`";
    }

    private class PlayerEmbedPaginator {
        @Nonnull
        private final MessageEmbed template;
        @Nonnull
        private final List<SmashdataManager.PlayerData> results;

        private int resultPage;

        public PlayerEmbedPaginator(@Nonnull MessageEmbed template, @Nonnull List<SmashdataManager.PlayerData> results) {
            this.template = template;
            this.results = results;

            resultPage = 0;
        }

        @Nonnull
        public synchronized Message nextResult(@Nonnull MessageReactionAddEvent e) {
            resultPage = (resultPage + 1) % results.size();
            return getCurrent();
        }

        @Nonnull
        public synchronized Message prevResult(@Nonnull MessageReactionAddEvent e) {
            resultPage--;
            if (resultPage < 0) resultPage = results.size() - 1;
            return getCurrent();
        }

        @Nonnull
        public synchronized Message getCurrent() {
            try {
                EmbedBuilder embed = new EmbedBuilder(template);

                applyData(embed, results, resultPage);

                return new MessageBuilder().setEmbeds(embed.build()).build();
            } catch (Exception e) {
                log.catching(e);
                return new MessageBuilder("There was a severe unexpected problem with displaying the player data, I don't really know how that happened. I'll tell  my dev, you can go shoot them a message about this too if you want to.").build();
            }
        }
    }
}
