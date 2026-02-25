package com.energomonitor.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface EnergomonitorApiService {

    @POST("authorizations")
    suspend fun createAuthorization(
        @Header("Authorization") basicAuthHeader: String,
        @Body request: AuthorizationRequest = AuthorizationRequest()
    ): AuthorizationResponse

    @GET("users/{user_id}/feeds")
    suspend fun getFeeds(@Path("user_id") userId: String): List<FeedDto>

    @GET("feeds/{feed_id}/streams")
    suspend fun getStreams(@Path("feed_id") feedId: String): List<StreamDto>

    @GET("feeds/{feed_id}/streams/{stream_id}/data")
    suspend fun getStreamData(
        @Path("feed_id") feedId: String,
        @Path("stream_id") streamId: String,
        @Query("limit") limit: Int = 1,
        @Query("time_from") timeFrom: Long? = null,
        @Query("time_to") timeTo: Long? = null
    ): List<List<Double>>
}

// DTOs

@Serializable
data class AuthorizationRequest(
    val note: String = "Android Sensor Data App Token",
    val valid_minutes: Int = 43200 // 30 days
)

@Serializable
data class AuthorizationResource(
    val type: String,
    val name: String,
    val permissions: List<String>
)

@Serializable
data class AuthorizationResponse(
    val id: Int,
    val user_id: String,
    val token: String,
    val note: String? = null,
    val resources: List<AuthorizationResource> = emptyList(),
    val expires_at: String
)

@Serializable
data class FeedDto(
    val id: String,
    val configs: List<FeedConfigDto>? = null
)

@Serializable
data class FeedConfigDto(
    val title: String? = null,
    val timezone: String? = null
)

@Serializable
data class StreamDto(
    val id: String,
    val type: String? = null,
    val configs: List<StreamConfigDto>? = null,
    val medium: Int? = null // It can be an int or a string mapping in some endpoints, but let's stick to standard payload
)

@Serializable
data class StreamConfigDto(
    val title: String? = null,
    val medium: String? = null,
    val unit: String? = null
)
