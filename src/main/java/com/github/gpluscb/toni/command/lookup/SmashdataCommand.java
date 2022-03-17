package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.toni.command.*;
import com.github.gpluscb.toni.smashdata.SmashdataManager;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.MiscUtil;
import com.github.gpluscb.toni.util.OneOfTwo;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.github.gpluscb.toni.menu.ReactionActionMenu;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("ClassCanBeRecord")
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
    public void execute(@Nonnull CommandContext<?> ctx) {
        String requestedTag;

        OneOfTwo<MessageCommandContext, SlashCommandContext> context = ctx.getContext();
        if (context.isT()) {
            MessageCommandContext msg = context.getTOrThrow();

            if (msg.getArgs().isEmpty()) {
                ctx.reply("Too few arguments. I can't just find you a player if I don't know what you're searching for. Use `/help player` for help.").queue();
                return;
            }

            requestedTag = msg.getArgsFrom(0);

            ctx.getChannel().sendTyping().queue();
        } else {
            SlashCommandContext slash = context.getUOrThrow();

            requestedTag = slash.getOptionNonNull("tag").getAsString();

            slash.getEvent().deferReply().queue();
        }

        try {
            List<SmashdataManager.PlayerData> results = smashdata.loadSmashdataByTag(requestedTag);

            Comparator<SmashdataManager.PlayerData> comp = (a, b) -> {
                // Find the higher ranked player
                Integer aPgru = a.pgru();
                Integer bPgru = b.pgru();
                if (aPgru != null) {
                    if (bPgru == null) return 1;
                    else return -Integer.compare(aPgru, bPgru);
                } else if (bPgru != null) {
                    return -1;
                }

                // Find player with socials
                int aSocial = a.social().twitter().size();
                int bSocial = b.social().twitter().size();
                if (aSocial != bSocial) return Integer.compare(aSocial, bSocial);

                // Find player with higher character count
                int aCount = a.characters().values().stream().mapToInt(c -> c).sum();
                int bCount = b.characters().values().stream().mapToInt(c -> c).sum();
                if (aCount != bCount) return Integer.compare(aCount, bCount);

                int aTotalData = 0;
                if (a.state() != null && !a.state().isEmpty()) aTotalData++;
                if (a.country() != null && !a.country().isEmpty()) aTotalData++;
                if (!a.prefixes().isEmpty()) aTotalData++;
                if (a.region() != null && !a.region().isEmpty()) aTotalData++;

                int bTotalData = 0;
                if (b.state() != null && !b.state().isEmpty()) bTotalData++;
                if (b.country() != null && !b.country().isEmpty()) bTotalData++;
                if (!b.prefixes().isEmpty()) bTotalData++;
                if (b.region() != null && !b.region().isEmpty()) bTotalData++;

                return Integer.compare(aTotalData, bTotalData);
            };

            results.sort(comp.reversed());

            sendReply(ctx, results);
        } catch (SQLException e) {
            ctx.reply("Something with the database for the players is broken :( I will notify my dev, but you can give them some context too if you want to.").queue();
            log.catching(e);
        }
    }

    private void sendReply(@Nonnull CommandContext<?> ctx, @Nonnull List<SmashdataManager.PlayerData> results) {
        if (results.isEmpty()) {
            ctx.reply("I couldn't find anything for that input, sorry.").queue();
            return;
        }

        User author = ctx.getUser();
        Member member = ctx.getMember();

        PlayerEmbedPaginator pages = new PlayerEmbedPaginator(EmbedUtil.getPreparedSmashdata(member, author).build(), results);
        ReactionActionMenu.Builder menuBuilder = new ReactionActionMenu.Builder()
                .setEventWaiter(waiter)
                .addUsers(author.getIdLong())
                .setStart(pages.getCurrent());

        if (results.size() > 1) {
            menuBuilder.registerButton(Constants.ARROW_BACKWARD, pages::prevResult)
                    .registerButton(Constants.ARROW_FORWARD, pages::nextResult);
        }

        ReactionActionMenu menu = menuBuilder.build();

        ctx.getContext()
                .onT(msg -> menu.displayReplying(msg.getMessage()))
                .onU(slash -> menu.displaySlashCommandDeferred(slash.getEvent()));
    }

    @Nonnull
    private EmbedBuilder applyData(@Nonnull EmbedBuilder builder, @Nonnull List<SmashdataManager.PlayerData> players, int idx) {
        SmashdataManager.PlayerData data = players.get(idx);
        String title = players.size() > 1 ?
                String.format("(%d/%d) Smasher: %s", idx + 1, players.size(), data.tag())
                : String.format("Smasher: %s", data.tag());

        String url = String.format("https://smashdata.gg/smash/ultimate/player/%s?id=%s",
                URLEncoder.encode(data.tag(), Charset.defaultCharset()),
                URLEncoder.encode(data.id(), Charset.defaultCharset()));

        builder.setTitle(title, url);

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        List<String> prefixes = data.prefixes();
        if (!prefixes.isEmpty())
            fields.add(new EmbedUtil.InlineField("Prefixes", String.join(", ", prefixes)));

        String country = data.country();
        if (country != null && !country.isEmpty()) {
            fields.add(new EmbedUtil.InlineField("Country", country));

            String state = data.state();
            if (state != null && !state.isEmpty())
                fields.add(new EmbedUtil.InlineField("State", state));

            String region = data.region();
            if (region != null && !region.isEmpty())
                fields.add(new EmbedUtil.InlineField("Region", region));
        }

        Map<String, Integer> characters = data.characters();
        if (!characters.isEmpty()) {
            // Filter for the ones they play more than 5% of the time
            int total = characters.values().stream().mapToInt(it -> it).sum();

            String mostPlayedChars = characters.entrySet().stream().filter(entry -> entry.getValue() / (double) total > .05)
                    .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                    .map(entry -> String.format("%s (%d)", MiscUtil.capitalizeFirst(entry.getKey().replace("ultimate/", "")), entry.getValue())).collect(Collectors.joining(", "));

            fields.add(new EmbedUtil.InlineField("Characters", mostPlayedChars));
        }

        Integer ranking = data.pgru();
        if (ranking != null)
            fields.add(new EmbedUtil.InlineField("PGRU Season 2", ranking.toString()));

        List<String> twitter = data.social().twitter();
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
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS})
                .setAliases(new String[]{"smashdata", "player", "smasher", "data"})
                .setShortHelp("Displays info about a smasher using data from [smashdata.gg](https://smashdata.gg). Usage: `smashdata <TAG...>`")
                .setDetailedHelp("`smashdata <TAG...>`\n" +
                        "Looks up a smash ultimate player by their tag using data from [smashdata.gg](https://smashdata.gg).\n" +
                        String.format("If there are multiple smashers matching the given tag, use the %s/%s to cycle through them.%n", Constants.ARROW_BACKWARD, Constants.ARROW_FORWARD) +
                        "Aliases: `smashdata`, `player`, `smasher`, `data`")
                .setCommandData(new CommandData("smasher", "Displays info about a smasher")
                        .addOption(OptionType.STRING, "tag", "The smasher's tag", true))
                .build();
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
                return new MessageBuilder("There was a severe unexpected problem with displaying the player data, I don't really know how that happened. I've told my dev, you can go shoot them a message about this too if you want to.").build();
            }
        }
    }
}
