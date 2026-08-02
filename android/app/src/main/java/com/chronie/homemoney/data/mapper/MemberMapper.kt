package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.remote.dto.MemberDto
import com.chronie.homemoney.domain.model.Member
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mapper for converting member data between the server DTO and domain model.
 *
 * Handles date string → epoch millis conversion for the [createdAt] and
 * [updatedAt] fields, using UTC timezone to avoid locale-specific offsets.
 */
object MemberMapper {
    /** Shared date formatter for parsing ISO 8601 timestamps from the server. */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Converts a server [MemberDto] to a domain [Member] model.
     *
     * @param dto The server-side member data.
     * @return A domain [Member] with epoch millis timestamps; defaults to
     *         current time if date parsing fails.
     */
    fun toDomain(dto: MemberDto): Member {
        return Member(
            id = dto.id,
            username = dto.username,
            createdAt = parseDate(dto.createdAt),
            updatedAt = parseDate(dto.updatedAt),
            avatar = dto.avatar
        )
    }

    /**
     * Parses an ISO 8601 date string to epoch millis.
     * Falls back to [System.currentTimeMillis] on parse failure.
     */
    private fun parseDate(dateString: String): Long {
        return try {
            dateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
