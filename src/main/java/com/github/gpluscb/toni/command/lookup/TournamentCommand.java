package com.github.gpluscb.toni.command.lookup;

import com.github.gpluscb.ggjava.api.exception.DeserializationException;
import com.github.gpluscb.ggjava.api.exception.GGError;
import com.github.gpluscb.ggjava.entity.object.response.GGResponse;
import com.github.gpluscb.ggjava.entity.object.response.ListResponse;
import com.github.gpluscb.ggjava.entity.object.response.enums.ActivityStateResponse;
import com.github.gpluscb.ggjava.entity.object.response.enums.BracketTypeResponse;
import com.github.gpluscb.ggjava.entity.object.response.objects.*;
import com.github.gpluscb.ggjava.entity.object.response.scalars.*;
import com.github.gpluscb.toni.command.Command;
import com.github.gpluscb.toni.command.CommandContext;
import com.github.gpluscb.toni.command.CommandInfo;
import com.github.gpluscb.toni.menu.ReactionActionMenu;
import com.github.gpluscb.toni.startgg.GGManager;
import com.github.gpluscb.toni.util.Constants;
import com.github.gpluscb.toni.util.FailLogger;
import com.github.gpluscb.toni.util.Pair;
import com.github.gpluscb.toni.util.PairNonnull;
import com.github.gpluscb.toni.util.discord.EmbedUtil;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TournamentCommand implements Command {
    private static final Logger log = LogManager.getLogger(TournamentCommand.class);

    @Nonnull
    private final GGManager ggManager;
    @Nonnull
    private final EventWaiter waiter;

    public TournamentCommand(@Nonnull GGManager ggManager, @Nonnull EventWaiter waiter) {
        this.ggManager = ggManager;
        this.waiter = waiter;
    }

    @Override
    public void execute(@Nonnull CommandContext ctx) {
        String searchTerm = ctx.getOptionNonNull("search-term").getAsString();

        ctx.getEvent().deferReply().queue();

        // more than 7 -> risk of query complexiy
        ggManager.searchTouranmentsByName(searchTerm, 7, 8).whenComplete(FailLogger.logFail((response, t) -> {
            try {
                if (t != null) {
                    ctx.reply("The request to start.gg failed. Tell my dev if this happens a lot - I've already annoyed them about it, but it can't hurt to give them some more context.").queue();
                    log.catching(t);
                    return;
                }

                response.onU(errorResponse -> handleErrorResponse(ctx, errorResponse))
                        .onT(tournaments -> sendReply(ctx, tournaments));
            } catch (Exception e) {
                log.catching(e);
                ctx.reply("Ouch, an error. That one's probably on me, sorry. I'll send a report to my dev. If it keeps happening you might want to provide them with some context too.").queue();
            }
        }));
    }

    private void handleErrorResponse(@Nonnull CommandContext ctx, @Nonnull GGResponse<QueryResponse> errorResponse) {
        DeserializationException e = errorResponse.getException();
        List<GGError> errors = errorResponse.getErrors();
        if (e != null) log.catching(e);
        else if (errors != null)
            log.error("QueryResponse had errors, full response: " + errorResponse.getResponseRoot());
        else
            log.error("response.onError executed but neither exception nor errors field found, full response: " + errorResponse.getResponseRoot());

        ctx.reply("An error during the parsing of the response start.gg sent me... I'll go annoy my dev. If this happens consistently, go give them some context too.").queue();
    }

    private void sendReply(@Nonnull CommandContext ctx, @Nonnull List<TournamentResponse> tournaments) {
        if (tournaments.isEmpty()) {
            ctx.reply("Sorry, I couldn't find any tournament matching that on start.gg.").queue();
            return;
        }

        User author = ctx.getUser();
        Member member = ctx.getMember();

        TournamentEmbedPaginator pages = new TournamentEmbedPaginator(EmbedUtil.getPreparedGG(member, author).build(), tournaments);
        ReactionActionMenu.Builder menuBuilder = new ReactionActionMenu.Builder()
                .setEventWaiter(waiter)
                .addUsers(author.getIdLong())
                .registerButton(Emoji.fromUnicode(Constants.ARROW_DOWNWARD), pages::nextEvent)
                .registerButton(Emoji.fromUnicode(Constants.ARROW_UPWARD), pages::prevEvent)
                .setStart(pages.getCurrent());

        if (tournaments.size() > 1) {
            menuBuilder.registerButton(Emoji.fromUnicode(Constants.ARROW_BACKWARD), pages::prevTournament)
                    .registerButton(Emoji.fromUnicode(Constants.ARROW_FORWARD), pages::nextTournament);
        }

        ReactionActionMenu menu = menuBuilder.build();

        menu.displaySlashCommandDeferred(ctx.getEvent());
    }

    @Nonnull
    private EmbedBuilder applyOneTournament(@Nonnull EmbedBuilder builder, @Nonnull TournamentResponse tournament, @Nullable PairNonnull<Integer, Integer> idxOutOfSize) {
        // id shortSlug slug name hashtag venueAddress venueName startAt endAt registrationClosesAt isOnline numAttendees primaryContact primaryContactType url(relative:false) images{width height ratio url} links{facebook discord} events{slug name}
        StringResponse nameResponse = tournament.getName();
        String name = nameResponse == null ? "[not named]" : nameResponse.getValue();
        String title = idxOutOfSize != null ?
                String.format("(%d/%d) %s", idxOutOfSize.getT(), idxOutOfSize.getU(), name)
                : name;
        StringResponse urlResponse = tournament.getUrl();

        builder.setTitle(title, urlResponse == null ? null : urlResponse.getValue());

        List<ImageResponse> images = tournament.getImages();
        if (images != null)
            findBestThumbnail(images).map(ImageResponse::getUrl).map(StringResponse::getValue).ifPresent(builder::setThumbnail);

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        boolean isOnline = tournament.getIsOnline().getValue();
        fields.add(new EmbedUtil.InlineField("Type", isOnline ? "Online" : "Offline"));

        StringResponse venueName = tournament.getVenueName();
        StringResponse venueAddress = tournament.getVenueAddress();
        if (venueName != null && venueAddress != null)
            fields.add(new EmbedUtil.InlineField("Venue", String.format("%s | %s", venueName.getValue(), venueAddress.getValue())));
        else if (venueName != null)
            fields.add(new EmbedUtil.InlineField("Venue", venueName.getValue()));
        else if (venueAddress != null)
            fields.add(new EmbedUtil.InlineField("Venue address", venueAddress.getValue()));

        IntResponse numAttendees = tournament.getNumAttendees();
        if (numAttendees != null)
            fields.add(new EmbedUtil.InlineField("Attendees", String.valueOf(numAttendees.getValue())));

        StringResponse contact = tournament.getPrimaryContact();
        if (contact != null)
            fields.add(new EmbedUtil.InlineField("Contact", contact.getValue()));

        TournamentLinksResponse links = tournament.getLinks();
        StringResponse discord = links == null ? null : links.getDiscord();
        StringResponse facebook = links == null ? null : links.getFacebook();
        StringResponse hashtag = tournament.getHashtag();

        if (discord != null || facebook != null || hashtag != null) {
            String discordString = discord == null ? null : String.format("[Discord](%s)", discord.getValue());
            String facebookString = facebook == null ? null : String.format("[Facebook](%s)", facebook.getValue());
            String hashtagString = hashtag == null ? null : String.format("[#%s](https://twitter.com/hashtag/%1$s)", hashtag.getValue());

            String linksString = Stream.of(discordString, facebookString, hashtagString).filter(Objects::nonNull).collect(Collectors.joining(" | "));

            fields.add(new EmbedUtil.InlineField("Social", linksString));
        }

        StringResponse slug = tournament.getShortSlug();
        if (slug == null) slug = tournament.getSlug();
        if (slug != null) fields.add(new EmbedUtil.InlineField("Slug", slug.getValue()));

        IDResponse id = tournament.getId();
        if (id != null) fields.add(new EmbedUtil.InlineField("ID", id.getValue()));

        TimestampResponse startAt = tournament.getStartAt();
        TimestampResponse endAt = tournament.getEndAt();
        OffsetDateTime startAtDate = startAt == null ? null : OffsetDateTime.ofInstant(startAt.getValueInstant(), Constants.TIME_ZONE);
        OffsetDateTime endAtDate = endAt == null ? null : OffsetDateTime.ofInstant(endAt.getValueInstant(), Constants.TIME_ZONE);
        if (startAtDate != null && endAtDate != null)
            fields.add(new EmbedUtil.InlineField("Date", String.format("%s - %s", Constants.DATE_FORMAT.format(startAtDate), Constants.DATE_FORMAT.format(endAtDate))));
        else if (startAtDate != null)
            fields.add(new EmbedUtil.InlineField("Starts", Constants.DATE_FORMAT.format(OffsetDateTime.now(Constants.TIME_ZONE))));

        builder.setDescription(EmbedUtil.parseInlineFields(fields));

        ListResponse<EventResponse> events = tournament.getEvents();

        if (events != null && !events.isEmpty()) {
            StringBuilder currentField = new StringBuilder();

            for (EventResponse event : events) {
                if (event == null) continue;

                StringResponse eventName = event.getName();
                String eventNameValue = eventName == null ? "\\[not named\\]" : eventName.getValue();

                StringResponse eventSlug = event.getSlug();

                String eventString = eventSlug == null ?
                        String.format("• `%s`", eventNameValue) :
                        String.format("• [`%s`](https://start.gg/%s)", eventNameValue, eventSlug.getValue());

                if (currentField.length() + eventString.length() > MessageEmbed.VALUE_MAX_LENGTH) {
                    builder.addField("Events", currentField.toString().stripTrailing(), true);
                    currentField = new StringBuilder();
                } else {
                    currentField.append(eventString);
                    currentField.append('\n');
                }
            }

            if (!currentField.isEmpty())
                builder.addField("Events", currentField.toString().stripTrailing(), true);
        }

        return builder;
    }

    @Nonnull
    private EmbedBuilder applyOneEvent(@Nonnull EmbedBuilder builder, @Nonnull TournamentResponse tournament, @Nonnull EventResponse event) {
        // id slug name state startAt numEntrants images{...i} videogame{images{...i} displayName} phases{name bracketType} standings(query:{page:1 perPage:8}){nodes{placement entrant{name}}}
        StringResponse nameResponse = event.getName();
        String name = nameResponse == null ? "[not named]" : nameResponse.getValue();
        StringResponse slugResponse = event.getSlug();
        String url = slugResponse == null ? null : String.format("https://start.gg/%s", event.getSlug().getValue());

        StringResponse tournamentNameResponse = tournament.getName();
        String tournamentName = tournamentNameResponse == null ? "[not named]" : tournamentNameResponse.getValue();

        builder.setTitle(String.format("%s - %s", name, tournamentName), url);

        Optional<String> imageUrl = Optional.empty();
        List<ImageResponse> images = event.getImages();
        if (images != null)
            imageUrl = findBestThumbnail(images).map(ImageResponse::getUrl).map(StringResponse::getValue);

        List<EmbedUtil.InlineField> fields = new ArrayList<>();

        VideogameResponse videogame = event.getVideogame();
        if (videogame != null) {
            if (imageUrl.isEmpty()) {
                List<ImageResponse> videogameImages = videogame.getImages();
                if (videogameImages != null)
                    imageUrl = findBestThumbnail(videogameImages).map(ImageResponse::getUrl).map(StringResponse::getValue);
            }

            StringResponse videogameName = videogame.getDisplayName();
            if (videogameName != null) fields.add(new EmbedUtil.InlineField("Game", videogameName.getValue()));
        }

        imageUrl.ifPresent(builder::setThumbnail);

        ActivityStateResponse stateResponse = event.getState();
        if (stateResponse != null) {
            String state = switch (stateResponse.getValue()) {
                case CREATED -> "Created";
                case ACTIVE -> "Active";
                case COMPLETED -> "Completed";
                case READY -> "Ready";
                case INVALID -> "Invalid";
                case CALLED -> "Called";
                case QUEUED -> "Queued";
            };
            fields.add(new EmbedUtil.InlineField("State", state));
        }

        TimestampResponse startAt = event.getStartAt();
        OffsetDateTime startAtDate = startAt == null ? null : OffsetDateTime.ofInstant(startAt.getValueInstant(), Constants.TIME_ZONE);
        if (startAtDate != null)
            fields.add(new EmbedUtil.InlineField("Starts", Constants.DATE_FORMAT.format(OffsetDateTime.now(Constants.TIME_ZONE))));

        builder.setDescription(EmbedUtil.parseInlineFields(fields));

        List<PhaseResponse> brackets = event.getPhases();
        if (brackets != null) {
            String bracketString = brackets.stream().filter(Objects::nonNull).map(bracket -> {
                StringBuilder stringBuilder = new StringBuilder();

                StringResponse bracketNameResponse = bracket.getName();
                String bracketName = bracketNameResponse == null ? "\\[not named\\]" : bracketNameResponse.getValue();
                stringBuilder.append(String.format("`%s`", bracketName));

                BracketTypeResponse type = bracket.getBracketType();
                if (type != null) {
                    String bracketType = switch (type.getValue()) {
                        case SINGLE_ELIMINATION -> "SE";
                        case DOUBLE_ELIMINATION -> "DE";
                        case ROUND_ROBIN -> "RR";
                        case SWISS -> "Swiss";
                        case EXHIBITION -> "Exhibition";
                        case CUSTOM_SCHEDULE -> "Custom schedule";
                        case MATCHMAKING -> "Matchmaking";
                        case ELIMINATION_ROUNDS -> "Elimination rounds";
                        case RACE -> "Race";
                        default -> null;
                    };
                    stringBuilder.append(String.format(" `%s`", bracketType));
                }

                String result = stringBuilder.toString();
                return result.isEmpty() ? null : result;
            }).filter(Objects::nonNull).collect(Collectors.joining("\n"));

            if (!bracketString.isEmpty()) builder.addField("Brackets", bracketString, false);
        }

        StandingConnectionResponse standingConnection = event.getStandings();
        if (standingConnection != null) {
            List<StandingResponse> standings = standingConnection.getNodes();
            if (standings != null) {
                String standingsString = standings.stream().filter(Objects::nonNull).map(standing -> {
                    IntResponse placementResponse = standing.getPlacement();
                    Integer placement = placementResponse == null ? null : placementResponse.getValue();
                    if (placement != null && placement == 0) placement = null;
                    EntrantResponse entrant = standing.getEntrant();
                    StringResponse entrantNameResponse = entrant == null ? null : entrant.getName();
                    String entrantName = entrantNameResponse == null ? "\\[not named\\]" : entrantNameResponse.getValue();

                    if (placement != null) return String.format("%d %s", placement, entrantName);
                    else return entrantName;
                }).filter(Predicate.not(String::isEmpty)).collect(Collectors.joining("\n"));

                if (!standingsString.isEmpty())
                    builder.addField("Standings", String.format("```%s```", standingsString), false);
            }
        }

        return builder;
    }

    @Nonnull
    private Optional<ImageResponse> findBestThumbnail(@Nonnull List<ImageResponse> images) {
        // TODO: This is way over engineered, just look for type profile or what it was called
        // Show image with aspect ratio closest to one, or the one with highest resolution if two are the same; looks best in thumbnail
        return images.stream().filter(image -> image != null && image.getUrl() != null).max((a, b) -> {
            FloatResponse aRatio = a.getRatio();
            FloatResponse bRatio = b.getRatio();

            if (aRatio != null && bRatio != null) {
                // Lower ratio is better -> invert
                int result = -Float.compare(Math.abs(a.getRatio().getValue() - 1), Math.abs(b.getRatio().getValue() - 1));
                if (result != 0) return result;
            } else if (aRatio != null) return 1;
            else if (bRatio != null) return -1;

            FloatResponse aWidth = a.getWidth();
            FloatResponse aHeight = a.getHeight();
            Float aRes = aWidth == null || aHeight == null ? null : aWidth.getValue() * aHeight.getValue();
            FloatResponse bWidth = b.getWidth();
            FloatResponse bHeight = b.getHeight();
            Float bRes = bWidth == null || bHeight == null ? null : bWidth.getValue() * bHeight.getValue();

            if (aRes != null && bRes != null) return Float.compare(aRes, bRes);
            else if (aRes != null) return 1;
            else if (bRes != null) return -1;
            else return 0;
        });
    }

    @Nonnull
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo.Builder()
                .setRequiredBotPerms(new Permission[]{Permission.MESSAGE_EMBED_LINKS})
                .setShortHelp("Finds and displays tournaments from [start.gg](https://start.gg).`")
                .setDetailedHelp(String.format("""
                                `tournament <SEARCH TERM...>`
                                Searches for tournaments on [start.gg](https://start.gg) by their name, id, or slug (end of url).
                                If there are multiple tournaments matching the given term, use the %s/%s reactions to cycle through them.
                                Use the %s/%s reactions to cycle through events in a tournament.
                                Slash command options:
                                • `search-term`: The search term (e.g. the end of the tournament url).""",
                        Constants.ARROW_BACKWARD, Constants.ARROW_FORWARD,
                        Constants.ARROW_DOWNWARD, Constants.ARROW_UPWARD))
                .setCommandData(Commands.slash("tournament", "Displays info about a tournament")
                        .addOption(OptionType.STRING, "search-term", "The search term (e.g. the end of the tournament url)", true))
                .build();
    }

    private class TournamentEmbedPaginator {
        @Nonnull
        private final MessageEmbed template;
        @Nonnull
        private final List<PairNonnull<TournamentResponse, List<EventResponse>>> tournaments;
        /**
         * First index: tournament page
         * Second index: event page (0 for none)
         */
        // TODO: Is this really practical? I mean I guess I have it for this so...
        @Nonnull
        private final MessageCreateData[][] lazyMessages;

        private int tournamentPage;
        /**
         * The tournamentPage is eventPage 0, all event indices are eventPage-1
         */
        private int eventPage;

        public TournamentEmbedPaginator(@Nonnull MessageEmbed template, @Nonnull List<TournamentResponse> tournaments) {
            this.template = template;
            this.tournaments = tournaments.stream().map(tournament -> {
                List<EventResponse> eventsResponse = tournament.getEvents();
                List<EventResponse> events = eventsResponse == null ?
                        Collections.emptyList()
                        : eventsResponse.stream().filter(Objects::nonNull).toList();

                return new PairNonnull<>(tournament, events);
            }).toList();

            lazyMessages = new MessageCreateData[tournaments.size()][];
            for (int i = 0; i < lazyMessages.length; i++) {
                List<EventResponse> events = tournaments.get(i).getEvents();
                lazyMessages[i] = new MessageCreateData[events == null ? 1 : events.size() + 1];
            }
            tournamentPage = 0;
            eventPage = 0;
        }

        @Nonnull
        public synchronized MessageEditData nextTournament(@Nonnull MessageReactionAddEvent e) {
            tournamentPage = (tournamentPage + 1) % tournaments.size();
            eventPage = 0;
            return MessageEditData.fromCreateData(getCurrent());
        }

        @Nonnull
        public synchronized MessageEditData prevTournament(@Nonnull MessageReactionAddEvent e) {
            tournamentPage--;
            if (tournamentPage < 0) tournamentPage = tournaments.size() - 1;
            eventPage = 0;
            return MessageEditData.fromCreateData(getCurrent());
        }

        @Nonnull
        public synchronized MessageEditData nextEvent(@Nonnull MessageReactionAddEvent e) {
            eventPage = (eventPage + 1) % (tournaments.get(tournamentPage).getU().size() + 1);
            return MessageEditData.fromCreateData(getCurrent());
        }

        @Nonnull
        public synchronized MessageEditData prevEvent(@Nonnull MessageReactionAddEvent e) {
            eventPage--;
            if (eventPage < 0) eventPage = tournaments.get(tournamentPage).getU().size();
            return MessageEditData.fromCreateData(getCurrent());
        }

        @Nonnull
        public synchronized MessageCreateData getCurrent() {
            try {
                MessageCreateData lazyMessage = lazyMessages[tournamentPage][eventPage];
                if (lazyMessage != null) return lazyMessage;

                EmbedBuilder embed = new EmbedBuilder(template);

                Pair<TournamentResponse, List<EventResponse>> pair = tournaments.get(tournamentPage);
                TournamentResponse tournament = pair.getT();
                PairNonnull<Integer, Integer> idxOutOfSize = tournaments.size() > 1 ?
                        new PairNonnull<>(tournamentPage + 1, tournaments.size())
                        : null;

                MessageCreateData message;
                if (eventPage == 0)
                    message = new MessageCreateBuilder().setEmbeds(applyOneTournament(embed, tournament, idxOutOfSize).build()).build();
                else {
                    int eventIndex = eventPage - 1;
                    EventResponse event = pair.getU().get(eventIndex);
                    message = new MessageCreateBuilder().setEmbeds(applyOneEvent(embed, tournament, event).build()).build();
                }
                lazyMessages[tournamentPage][eventPage] = message;
                return message;
            } catch (Exception e) {
                log.catching(e);
                return new MessageCreateBuilder()
                        .setContent("There was an error with displaying the tournament, looks like start.gg sent me unexpected data (or the other way around...). I'll tell  my dev, you can go shoot them a message about this too if you want to.")
                        .build();
            }
        }
    }
}
