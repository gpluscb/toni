package com.github.gpluscb.toni.toni_api;

import com.github.gpluscb.toni.toni_api.model.GuildUserResponse;
import com.github.gpluscb.toni.toni_api.model.SetIdResponse;
import com.github.gpluscb.toni.toni_api.model.SmashSet;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

import javax.annotation.Nonnull;
import java.util.List;

public interface ToniApiService {
    @GET("ranking/{guildId}/{userId}/user_ranking.json")
    @Nonnull
    Call<GuildUserResponse> guildUserRanking(@Path("guildId") long guildId, @Path("userId") long userId);

    @GET("ranking/guild/{guildId}/rankings.json")
    @Nonnull
    Call<List<GuildUserResponse>> guildRankings(@Path("guildId") long guildId);

    @GET("ranking/user/{userId}/user_guilds.json")
    @Nonnull
    Call<List<GuildUserResponse>> userGuilds(@Path("userId") long userId);

    @GET("games/{setId}/get_set.json")
    @Nonnull
    Call<SmashSet> ratedSet(@Path("setId") long setId);

    @GET("games/user/{userId}/get_user_sets.json")
    @Nonnull
    Call<List<SmashSet>> userSets(@Path("userId") long userId);

    @POST("ranking/games/register.json")
    @Nonnull
    Call<SetIdResponse> registerRatedSet(@Body @Nonnull SmashSet set);
}
