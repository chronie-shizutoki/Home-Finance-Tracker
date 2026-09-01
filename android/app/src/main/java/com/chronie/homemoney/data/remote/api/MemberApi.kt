package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.ApiResponse
import com.chronie.homemoney.data.remote.dto.HealthDto
import com.chronie.homemoney.data.remote.dto.MemberDto
import com.chronie.homemoney.data.remote.dto.MemberRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import com.google.gson.annotations.SerializedName

/**
 * Retrofit API interface for member and health check endpoints.
 *
 * Supports member registration/lookup, profile retrieval, and avatar updates.
 * Also exposes a lightweight health check endpoint for verifying server connectivity.
 */
interface MemberApi {

    /**
     * Checks server health status.
     *
     * @return [HealthDto] with status, timestamp, and database connectivity state.
     */
    @GET("api/health/lite")
    suspend fun checkHealth(): HealthDto

    /**
     * Retrieves an existing member or creates one if not found.
     *
     * @param request Contains the username to look up or register.
     * @return An [ApiResponse] wrapping the [MemberDto] for the requested username.
     */
    @POST("api/members/members")
    suspend fun getOrCreateMember(@Body request: MemberRequest): ApiResponse<MemberDto>

    /**
     * Fetches member profile information by username.
     *
     * @param username The unique username to query.
     * @return An [ApiResponse] wrapping the member's [MemberDto].
     */
    @GET("api/members/members/{username}")
    suspend fun getMemberInfo(@Path("username") username: String): ApiResponse<MemberDto>

    /**
     * Updates the avatar image for a member.
     *
     * @param username The member whose avatar to update.
     * @param request Body containing the new avatar data (base64 or URL).
     * @return An [ApiResponse] wrapping the updated [MemberDto].
     */
    @PUT("api/members/members/{username}/avatar")
    suspend fun updateAvatar(@Path("username") username: String, @Body request: AvatarUpdateRequest): ApiResponse<MemberDto>
}

data class AvatarUpdateRequest(
    @SerializedName("avatar")
    val avatar: String
)
