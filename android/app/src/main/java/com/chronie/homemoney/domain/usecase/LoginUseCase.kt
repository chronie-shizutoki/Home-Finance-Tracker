package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.domain.model.Member
import com.chronie.homemoney.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * Handles user login by looking up an existing member or creating a new one.
 *
 * On successful login, the username is persisted to [PreferencesManager]
 * so the welcome screen can be skipped on future app launches.
 *
 * @param memberRepository Repository for member lookup and creation.
 * @param preferencesManager Local preferences store for persisting login state.
 */
class LoginUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val preferencesManager: PreferencesManager
) {
    /**
     * Attempts to log in with the given username.
     *
     * If the username exists on the server, the existing member is returned.
     * If not, a new member is created. The username is saved locally on success.
     *
     * @param username The display name to log in with. Must not be blank.
     * @return [Result.success] with the [Member] on successful login,
     *         or [Result.failure] if the username is blank or the server call fails.
     */
    suspend operator fun invoke(username: String): Result<Member> {
        if (username.isBlank()) {
            return Result.failure(Exception("Username cannot be empty"))
        }

        return memberRepository.getOrCreateMember(username).also { result ->
            if (result.isSuccess) {
                // Persist logged-in username so the welcome screen is skipped next time
                preferencesManager.saveUsername(username)
            }
        }
    }
}
