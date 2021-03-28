package com.github.gpluscb.toni.statsposting.topgg;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface TopggService {
    @POST("bots/{id}/stats")
    Call<JsonObject> postStats(@Header("Authorization") String token, @Path("id") long id, @Body JsonObject body);
}
