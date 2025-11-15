package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.data.local.PreferencesManager
import com.chronie.homemoney.domain.model.SubscriptionStatus
import com.chronie.homemoney.domain.repository.MemberRepository
import javax.inject.Inject

class GetMembershipStatusUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val preferencesManager: PreferencesManager
) {
    suspend operator fun invoke(username: String, forceRefresh: Boolean = false): Result<SubscriptionStatus> {
        // 如果不强制刷新，先检查本地缓存
        if (!forceRefresh) {
            val lastCheckTime = preferencesManager.getMembershipLastCheckTime()
            val cacheAge = System.currentTimeMillis() - lastCheckTime
            
            // 如果缓存时间小于5分钟，使用缓存
            if (cacheAge < 5 * 60 * 1000) {
                val isActive = preferencesManager.isMembershipActive()
                val planName = preferencesManager.getMembershipPlanName()
                val endDate = preferencesManager.getMembershipEndDate()
                
                return Result.success(
                    SubscriptionStatus(
                        isActive = isActive,
                        planName = planName,
                        planDescription = null,
                        startDate = null,
                        endDate = endDate,
                        status = if (isActive) "active" else null,
                        autoRenew = false
                    )
                )
            }
        }
        
        // 从服务器获取最新状态
        return try {
            val result = memberRepository.getCurrentSubscription(username)
            
            if (result.isSuccess) {
                val status = result.getOrNull()!!
                // 缓存到本地
                preferencesManager.saveMembershipStatus(
                    isActive = status.isActive,
                    planName = status.planName,
                    endDate = status.endDate
                )
                Result.success(status)
            } else {
                // 网络错误时，使用本地缓存
                val isActive = preferencesManager.isMembershipActive()
                val planName = preferencesManager.getMembershipPlanName()
                val endDate = preferencesManager.getMembershipEndDate()
                
                Result.success(
                    SubscriptionStatus(
                        isActive = isActive,
                        planName = planName,
                        planDescription = null,
                        startDate = null,
                        endDate = endDate,
                        status = if (isActive) "active" else null,
                        autoRenew = false
                    )
                )
            }
        } catch (e: Exception) {
            // 异常时使用本地缓存
            val isActive = preferencesManager.isMembershipActive()
            val planName = preferencesManager.getMembershipPlanName()
            val endDate = preferencesManager.getMembershipEndDate()
            
            Result.success(
                SubscriptionStatus(
                    isActive = isActive,
                    planName = planName,
                    planDescription = null,
                    startDate = null,
                    endDate = endDate,
                    status = if (isActive) "active" else null,
                    autoRenew = false
                )
            )
        }
    }
}
