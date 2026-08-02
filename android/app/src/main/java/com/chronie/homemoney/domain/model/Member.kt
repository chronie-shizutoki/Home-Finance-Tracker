package com.chronie.homemoney.domain.model

/**
 * Represents a household member / user in the home finance system.
 *
 * Each member has a unique identity within the household and can optionally
 * have an avatar image.
 *
 * @property id Unique identifier for this member.
 * @property username Display name used for login and identification.
 * @property createdAt Epoch millis timestamp when the member record was created.
 * @property updatedAt Epoch millis timestamp of the last profile update.
 * @property avatar Base64-encoded avatar image data (optional).
 */
data class Member(
    val id: String,
    val username: String,
    val createdAt: Long,
    val updatedAt: Long,
    val avatar: String? = null
)
