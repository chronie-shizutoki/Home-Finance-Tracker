package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MemberDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("isActive")
    val isActive: Boolean,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("updatedAt")
    val updatedAt: String
)

data class MemberRequest(
    @SerializedName("username")
    val username: String
)
