package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object for member profile information returned by the server.
 *
 * @property id Unique member identifier (UUID assigned by the server).
 * @property username Display name used for login, lookup, and identification.
 * @property createdAt ISO 8601 timestamp of when the member was first registered.
 * @property updatedAt ISO 8601 timestamp of the most recent profile modification.
 * @property avatar Base64-encoded image string or URL for the member's avatar. May be null
 *   if no avatar has been set.
 */
data class MemberDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("updatedAt")
    val updatedAt: String,
    @SerializedName("avatar")
    val avatar: String? = null
)

/**
 * Request body for creating or looking up a member by username.
 *
 * @property username The unique username to register or retrieve from the server.
 */
data class MemberRequest(
    @SerializedName("username")
    val username: String
)
