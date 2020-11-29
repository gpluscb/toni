package com.github.gpluscb.smashggnotifications.ultimateframedata;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface UltimateframedataService {
    @GET("character/{id}")
    Call<CharacterData> getCharacter(@Path("id") long id);
}
