package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.ApiResponse
import com.chronie.homemoney.data.remote.dto.MemberDto
import com.chronie.homemoney.data.remote.dto.MemberRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MemberApi {
    @POST("api/members/members")
    suspend fun getOrCreateMember(@Body request: MemberRequest): ApiResponse<MemberDto>

    @GET("api/members/members/{username}")
    suspend fun getMemberInfo(@Path("username") username: String): ApiResponse<MemberDto>
}
