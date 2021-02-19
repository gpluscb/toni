package com.github.gpluscb.smashggnotifications.smashgg;

import com.github.gpluscb.ggjava.api.GGClient;
import com.github.gpluscb.ggjava.api.exception.DeserializationException;
import com.github.gpluscb.ggjava.entity.object.response.GGResponse;
import com.github.gpluscb.ggjava.entity.object.response.objects.MutationResponse;
import com.github.gpluscb.ggjava.entity.object.response.objects.QueryResponse;
import com.github.gpluscb.ggjava.entity.object.response.objects.TournamentResponse;
import com.github.gpluscb.ggjava.entity.object.response.scalars.IDResponse;
import com.github.gpluscb.ggjava.entity.object.response.scalars.IntResponse;
import com.github.gpluscb.ggjava.entity.object.response.scalars.StringResponse;
import com.github.gpluscb.ggjava.internal.json.Deserializer;
import com.github.gpluscb.smashggnotifications.util.OneOfTwo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GGManager implements GGClient {
    private static final Logger log = LogManager.getLogger(GGManager.class);

    /**
     * The fragment is named "i"
     */
    @Nonnull
    private static final String IMAGE_FRAGMENT = "fragment i on Image{url ratio width height}";
    /**
     * This fragment is named "s"
     */
    @Nonnull
    private static final String STANDINGS_FRAGMENT = "fragment s on Standing{placement entrant{name}}";
    /**
     * The fragment is named "e"
     * Requires $numStandings variable
     * Includes {@link #IMAGE_FRAGMENT}, {@link #STANDINGS_FRAGMENT}
     */
    @Nonnull
    private static final String EVENT_INFO_FRAGMENT = "fragment e on Event{id slug name state startAt numEntrants images{...i} videogame{images{...i} displayName} phases{name bracketType} standings(query:{page:1 perPage:$numStandings}){nodes{...s}}} " + IMAGE_FRAGMENT + " " + STANDINGS_FRAGMENT;
    /**
     * The fragment is named "t"
     * Requires $numStandings variable
     * Includes {@link #EVENT_INFO_FRAGMENT} and therefore {@link #IMAGE_FRAGMENT} and {@link #STANDINGS_FRAGMENT}
     */
    @Nonnull
    private static final String TOURNAMENT_INFO_FRAGMENT = "fragment t on Tournament{id slug shortSlug name hashtag venueAddress venueName startAt endAt registrationClosesAt isOnline numAttendees primaryContact url(relative:false) images{...i} links{facebook discord} events{...e}} " + EVENT_INFO_FRAGMENT;
    @Nonnull
    private static final String TOURNAMENTS_QUERY = "query TournamentQuery($name:String!,$slug:String!,$id:ID,$numTournaments:Int!,$numStandings:Int!){tournament(slug:$slug,id:$id){...t} tournaments(query:{perPage:$numTournaments,filter:{name:$name}}){nodes{...t}}} " + TOURNAMENT_INFO_FRAGMENT;

    @Nonnull
    private static final String TOURNAMENTS_QUERY_ = "query TournamentQuery($ids:[ID!]!,$numTournaments:Int!,$numStandings:Int!){tournaments(query:{perPage:$numTournaments,filter:{ids:$ids}}){nodes{...t}}} " + TOURNAMENT_INFO_FRAGMENT;
    @Nonnull
    private static final String LIGHT_TOURNAMENT_INFO_FRAGMENT = "fragment t on Tournament{id name numAttendees}";
    @Nonnull
    private static final String LIGHT_TOURNAMENTS_QUERY = "query TournamentQuery($name:String!,$slug:String!,$id:ID,$numTournaments:Int!){tournament(slug:$slug,id:$id){...t} tournaments(query:{perPage:$numTournaments,filter:{name:$name}}){nodes{...t}}} " + LIGHT_TOURNAMENT_INFO_FRAGMENT;
    @Nonnull
    private static final String TOURNAMENTS_QUERY_WORKAROUND_FORMAT = "query TournamentsQuery($numStandings:Int!%s){%s} " + TOURNAMENT_INFO_FRAGMENT;
    @Nonnull
    private static final String TOURNAMENTS_QUERY_TOURNAMENT_ELEMENT_WORKAROUND_FORMAT = "t%d:tournament(id:$id%1$d){...t} ";

    @Nonnull
    private final Executor futureExecutor;

    @Nonnull
    private final GGClient client;
    @Nonnull
    private final List<String> stopwords;

    public GGManager(@Nonnull GGClient client, @Nonnull List<String> stopwords) {
        this.client = client;
        this.stopwords = stopwords;
        futureExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            int i;

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                return new Thread(runnable, String.format("GGManager [%d] Futures-Thread", i));
            }
        });
    }

    @Nonnull
    private static String generateTournamentsQueryWorkaround(int numTournaments) {
        StringBuilder paramsBuilder = new StringBuilder();
        StringBuilder tournamentElementsBuilder = new StringBuilder();

        for (int i = 0; i < numTournaments; i++) {
            paramsBuilder.append(",");
            paramsBuilder.append("$id").append(i).append(":ID!");
            tournamentElementsBuilder.append(String.format(TOURNAMENTS_QUERY_TOURNAMENT_ELEMENT_WORKAROUND_FORMAT, i));
        }

        return String.format(TOURNAMENTS_QUERY_WORKAROUND_FORMAT, paramsBuilder.toString(), tournamentElementsBuilder.toString());
    }

    @Nonnull
    private static JsonObject generateTournamentsQueryWorkaroundVariables(int numStandings, @Nonnull List<Long> ids) {
        JsonObject ret = new JsonObject();

        ret.addProperty("numStandings", numStandings);
        for (int i = 0; i < ids.size(); i++) ret.addProperty(String.format("id%d", i), ids.get(i));

        return ret;
    }

    /**
     * Order does not matter
     */
    @Nonnull
    private static List<TournamentResponse> deserializeTournamentsWorkaroundResponse(@Nonnull JsonObject response) throws DeserializationException {
        List<TournamentResponse> ret = new ArrayList<>();

        // It's fine if we throw here on error response I think
        for (Map.Entry<String, JsonElement> stringJsonElementEntry : response.getAsJsonObject("data").entrySet()) {
            JsonElement elem = stringJsonElementEntry.getValue();
            TournamentResponse deserialized = Deserializer.deserialize(elem, TournamentResponse.class);
            ret.add(deserialized);
        }

        return ret;
    }

    @Nonnull
    @Override
    public CompletableFuture<JsonObject> request(@Nonnull String query, @Nullable JsonObject variables) {
        return client.request(query, variables).thenApplyAsync(r -> {
            log.trace("query: \"{}\", variables: \"{}\" -> response: \"{}\"", query, variables, r);
            return r;
        }, futureExecutor);
    }

    @Nonnull
    @Override
    public CompletableFuture<GGResponse<QueryResponse>> query(@Nonnull String query, @Nullable JsonObject variables) {
        return client.query(query, variables).thenApplyAsync(r -> {
            log.trace("query: \"{}\", variables: \"{}\" -> response: \"{}\"", query, variables, r.getResponseRoot());
            return r;
        }, futureExecutor);
    }

    @Nonnull
    @Override
    public CompletableFuture<GGResponse<MutationResponse>> mutation(@Nonnull String query, @Nullable JsonObject variables) {
        return client.mutation(query, variables).thenApplyAsync(r -> {
            log.trace("query: \"{}\", variables: \"{}\" -> response: \"{}\"", query, variables, r.getResponseRoot());
            return r;
        }, futureExecutor);
    }

    @Nonnull
    public CompletableFuture<OneOfTwo<List<TournamentResponse>, GGResponse<QueryResponse>>> searchTouranmentsByName(@Nonnull String term, int numTournaments, int numStandings) {
        String[] split = term.replaceAll("[\\^%#}+*]", "").split("\\W+");
        String filteredTerm = Arrays.stream(split).filter(Predicate.not(stopwords::contains)).collect(Collectors.joining(" "));

        JsonObject lightVariables = new JsonObject();
        lightVariables.addProperty("slug", term);
        lightVariables.addProperty("name", filteredTerm);
        try {
            lightVariables.addProperty("id", Long.parseLong(filteredTerm));
        } catch (NumberFormatException ignored) {
        }
        lightVariables.addProperty("numTournaments", 500);

        return query(LIGHT_TOURNAMENTS_QUERY, lightVariables).<OneOfTwo<List<Long>, GGResponse<QueryResponse>>>thenApply(response ->
                response.map(OneOfTwo::ofU, success -> {
                    Stream<Long> idsStream = Stream.empty();

                    TournamentResponse slugTournament = success.getTournament();
                    if (slugTournament != null) {
                        IDResponse id = slugTournament.getId();
                        if (id != null) idsStream = Stream.concat(idsStream, Stream.of(id.getValueLong()));
                    }

                    List<TournamentResponse> tournaments = success.getTournaments().getNodes();
                    if (tournaments != null) {
                        idsStream = Stream.concat(idsStream, tournaments.stream().filter(Objects::nonNull).sorted((tournament1, tournament2) -> {
                            StringResponse name1 = tournament1.getName();
                            StringResponse name2 = tournament2.getName();

                            // Prio 1: Whatever tournament has a name
                            if (name1 == null || name2 == null) return Boolean.compare(name1 != null, name2 != null);

                            boolean name1Contains = name1.getValue().contains(term);
                            boolean name2Contains = name2.getValue().contains(term);

                            // Prio 2: For whatever tournament it contains the term in name
                            int comparingContains = Boolean.compare(name1Contains, name2Contains);
                            if (comparingContains != 0) return comparingContains;

                            IntResponse numAttendees1 = tournament1.getNumAttendees();
                            IntResponse numAttendees2 = tournament2.getNumAttendees();

                            // TODO: Priority 5 thing? Maybe recency?
                            // Prio 3: Whatever tournament has an attendant count
                            if (numAttendees1 == null || numAttendees2 == null)
                                return Boolean.compare(numAttendees1 != null, numAttendees2 != null);

                            // Prio 4: Whatever tournament has a higher attendant count
                            return Integer.compare(numAttendees1.getValue(), numAttendees2.getValue());
                        }).map(TournamentResponse::getId).filter(Objects::nonNull).map(IDResponse::getValueLong));
                    }

                    return OneOfTwo.ofT(idsStream.limit(numTournaments).collect(Collectors.toList()));
                })
        ).thenCompose(result ->
                result.map(ids -> {
                            String query = generateTournamentsQueryWorkaround(ids.size());
                            JsonObject variables = generateTournamentsQueryWorkaroundVariables(numStandings, ids);
                            return request(query, variables).thenCompose(json -> {
                                try {
                                    return CompletableFuture.completedFuture(GGManager.deserializeTournamentsWorkaroundResponse(json));
                                } catch (DeserializationException e) {
                                    return CompletableFuture.failedFuture(e);
                                }
                            }).thenApply(tournaments ->
                                    OneOfTwo.ofT(tournaments.stream()
                                            .filter(Objects::nonNull)
                                            .sorted(Comparator.comparingLong(tournament -> {
                                                IDResponse id = tournament.getId();
                                                return id == null ? 0 : ids.indexOf(id.getValueLong());
                                            })).collect(Collectors.toList()))
                            );
                        }, fail -> CompletableFuture.completedFuture(OneOfTwo.ofU(fail))
                )
        );
    }

    /* Below are other (abandoned) attempts at tournament search. Since tournament search is still not very good, I want to keep them just in case.
    /
     * Works, but abandoned in favour of workaround
     *
     * @deprecated
     /
    @Deprecated
    @Nonnull
    public CompletableFuture<OneOfTwo<List<TournamentResponse>, GGResponse<QueryResponse>>> searchTournamentsByName(@Nonnull String term, int numTournaments, int numStandings) {
        String[] split = term.replaceAll("[\\^%#}+*]", "").split("\\W+");
        String filteredTerm = Arrays.stream(split).filter(Predicate.not(stopwords::contains)).collect(Collectors.joining(" "));

        JsonObject variables = new JsonObject();
        variables.addProperty("slug", term);
        variables.addProperty("name", filteredTerm);
        try {
            variables.addProperty("id", Long.parseLong(filteredTerm));
        } catch (NumberFormatException ignored) {
        }
        variables.addProperty("numTournaments", numTournaments);
        variables.addProperty("numStandings", numStandings);

        return query(TOURNAMENTS_QUERY, variables).thenApply(response ->
                response.map(OneOfTwo::ofU, success -> {
                    TournamentResponse slugResponse = success.getTournament();
                    Stream<TournamentResponse> tournamentsStream = slugResponse == null ? Stream.empty() : Stream.of(success.getTournament());

                    List<TournamentResponse> tournaments = success.getTournaments().getNodes();
                    if (tournaments != null)
                        tournamentsStream = Stream.concat(tournamentsStream, tournaments.stream().filter(Objects::nonNull).sorted((tournament1, tournament2) -> {
                            StringResponse name1 = tournament1.getName();
                            StringResponse name2 = tournament2.getName();

                            // Prio 1: Whatever tournament has a name
                            if (name1 == null || name2 == null) return Boolean.compare(name1 != null, name2 != null);

                            boolean name1Contains = name1.getValue().contains(term);
                            boolean name2Contains = name2.getValue().contains(term);

                            // Prio 2: For whatever tournament it contains the term in name
                            int comparingContains = Boolean.compare(name1Contains, name2Contains);
                            if (comparingContains != 0) return comparingContains;

                            IntResponse numAttendees1 = tournament1.getNumAttendees();
                            IntResponse numAttendees2 = tournament2.getNumAttendees();

                            // TODO: Priority 5 thing? Maybe recency?
                            // Prio 3: Whatever tournament has an attendant count
                            if (numAttendees1 == null || numAttendees2 == null)
                                return Boolean.compare(numAttendees1 != null, numAttendees2 != null);

                            // Prio 4: Whatever tournament has a higher attendant count
                            return Integer.compare(numAttendees1.getValue(), numAttendees2.getValue());
                        }));

                    return OneOfTwo.ofT(tournamentsStream.collect(Collectors.toList()));
                }));
    }

    /
     * May return list of size numTournaments + 1 if slug matches
     *
     * @deprecated
     /
    @Nonnull
    @Deprecated
    public CompletableFuture<OneOfTwo<List<TournamentResponse>, GGResponse<QueryResponse>>> searchTournamentsByNameBrokenRn(@Nonnull String term, int numTournaments, int numStandings) {
        String[] split = term.replaceAll("[\\^%#}+*]", "").split("\\W+");
        String filteredTerm = Arrays.stream(split).filter(Predicate.not(stopwords::contains)).collect(Collectors.joining(" "));

        JsonObject lightVariables = new JsonObject();
        lightVariables.addProperty("slug", term);
        lightVariables.addProperty("name", filteredTerm);
        try {
            lightVariables.addProperty("id", Long.parseLong(filteredTerm));
        } catch (NumberFormatException ignored) {
        }
        lightVariables.addProperty("numTournaments", 100);

        return query(LIGHT_TOURNAMENTS_QUERY, lightVariables).<OneOfTwo<List<Long>, GGResponse<QueryResponse>>>thenApply(response ->
                response.map(OneOfTwo::ofU, success -> {
                    Stream<Long> idsStream = Stream.empty();

                    TournamentResponse slugTournament = success.getTournament();
                    if (slugTournament != null) {
                        IDResponse id = slugTournament.getId();
                        if (id != null) idsStream = Stream.concat(idsStream, Stream.of(id.getValueLong()));
                    }

                    List<TournamentResponse> tournaments = success.getTournaments().getNodes();
                    if (tournaments != null) {
                        idsStream = Stream.concat(idsStream, tournaments.stream().filter(Objects::nonNull).sorted((tournament1, tournament2) -> {
                            StringResponse name1 = tournament1.getName();
                            StringResponse name2 = tournament2.getName();

                            // Prio 1: Whatever tournament has a name
                            if (name1 == null || name2 == null) return Boolean.compare(name1 != null, name2 != null);

                            boolean name1Contains = name1.getValue().contains(term);
                            boolean name2Contains = name2.getValue().contains(term);

                            // Prio 2: For whatever tournament it contains the term in name
                            int comparingContains = Boolean.compare(name1Contains, name2Contains);
                            if (comparingContains != 0) return comparingContains;

                            IntResponse numAttendees1 = tournament1.getNumAttendees();
                            IntResponse numAttendees2 = tournament2.getNumAttendees();

                            // TODO: Priority 5 thing? Maybe recency?
                            // Prio 3: Whatever tournament has an attendant count
                            if (numAttendees1 == null || numAttendees2 == null)
                                return Boolean.compare(numAttendees1 != null, numAttendees2 != null);

                            // Prio 4: Whatever tournament has a higher attendant count
                            return Integer.compare(numAttendees1.getValue(), numAttendees2.getValue());
                        }).map(TournamentResponse::getId).filter(Objects::nonNull).map(IDResponse::getValueLong));
                    }

                    return OneOfTwo.ofT(idsStream.limit(numTournaments).collect(Collectors.toList()));
                })
        ).thenCompose(result ->
                result.map(ids -> {
                            JsonObject variables = new JsonObject();

                            JsonArray idsArray = new JsonArray();
                            ids.forEach(idsArray::add);
                            variables.add("ids", idsArray);

                            variables.addProperty("numTournaments", numTournaments);
                            variables.addProperty("numStandings", numStandings);

                            return query(TOURNAMENTS_QUERY_, variables).thenApply(ggResponse ->
                                    ggResponse.map(OneOfTwo::ofU, success -> {
                                        List<TournamentResponse> tournaments = success.getTournaments().getNodes();

                                        return OneOfTwo.ofT(tournaments == null ? Collections.emptyList()
                                                : tournaments.stream()
                                                .filter(Objects::nonNull)
                                                .sorted(Comparator.comparingLong(tournament -> {
                                                    IDResponse id = tournament.getId();
                                                    return id == null ? 0 : ids.indexOf(id.getValueLong());
                                                })).collect(Collectors.toList()));
                                    }));
                        }, fail -> CompletableFuture.completedFuture(OneOfTwo.ofU(fail))
                )
        );
/*
        return query(TOURNAMENTS_QUERY, lightVariables).thenApply(response ->
                response.map(OneOfTwo::ofU, success -> {
                    List<TournamentResponse> ret = new ArrayList<>();

                    TournamentResponse slugTournament = success.getTournament();
                    if (slugTournament != null) ret.add(slugTournament);

                    List<TournamentResponse> tournaments = success.getTournaments().getNodes();
                    if (tournaments != null) {
                        tournaments.stream().filter(Objects::nonNull).sorted((tournament1, tournament2) -> {
                            StringResponse name1 = tournament1.getName();
                            StringResponse name2 = tournament2.getName();

                            // Prio 1: Whatever tournament has a name
                            if (name1 == null || name2 == null) return Boolean.compare(name1 != null, name2 != null);

                            boolean name1Contains = name1.getValue().contains(term);
                            boolean name2Contains = name2.getValue().contains(term);

                            // Prio 2: For whatever tournament it contains the term in name
                            int comparingContains = Boolean.compare(name1Contains, name2Contains);
                            if (comparingContains != 0) return comparingContains;

                            IntResponse numAttendees1 = tournament1.getNumAttendees();
                            IntResponse numAttendees2 = tournament2.getNumAttendees();

                            // TODO: Priority 5 thing? Maybe recency?
                            // Prio 3: Whatever tournament has an attendant count
                            if (numAttendees1 == null || numAttendees2 == null)
                                return Boolean.compare(numAttendees1 != null, numAttendees2 != null);

                            // Prio 4: Whatever tournament has a higher attendant count
                            return Integer.compare(numAttendees1.getValue(), numAttendees2.getValue());
                        }).forEachOrdered(ret::add);
                    }

                    return OneOfTwo.ofT(ret);
                })
        );/
    }
*/
    @Nonnull
    @Override
    public CompletableFuture<Void> shutdown() {
        return client.shutdown();
    }

    @Override
    public boolean isShutDown() {
        return client.isShutDown();
    }
}