package com.chronie.homemoney.domain.repository

import com.chronie.homemoney.domain.model.Member

/**
 * Domain-layer contract for member data operations.
 *
 * Abstracts member registration/lookup, profile retrieval, and avatar management
 * behind suspend functions that return [Result]. This decouples domain logic
 * from the underlying data source (remote API, local cache, etc.) so that
 * use cases can depend on this interface without knowing the implementation details.
 */
interface MemberRepository {

    /**
     * Retrieves an existing member by username or creates one if not found.
     *
     * @param username The unique username to look up or register.
     * @return [Result.success] with the [Member] if successful, or [Result.failure] on error.
     */
    suspend fun getOrCreateMember(username: String): Result<Member>

    /**
     * Fetches full profile information for a given username.
     *
     * @param username The username whose profile to retrieve.
     * @return [Result.success] with the [Member], or [Result.failure] if the user is not found.
     */
    suspend fun getMemberInfo(username: String): Result<Member>

    /**
     * Updates the avatar image for a member.
     *
     * @param username The member whose avatar to update.
     * @param avatar The new avatar data as a base64-encoded string or URL.
     * @return [Result.success] with the updated [Member], or [Result.failure] on error.
     */
    suspend fun updateAvatar(username: String, avatar: String): Result<Member>
}
