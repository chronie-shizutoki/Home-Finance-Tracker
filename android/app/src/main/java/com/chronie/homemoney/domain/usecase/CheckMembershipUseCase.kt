package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.data.local.PreferencesManager
import javax.inject.Inject

/**
 * 检查会员状态的UseCase
 * 用于快速检查本地缓存的会员状态，不进行网络请求
 */
class CheckMembershipUseCase @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    operator fun invoke(): Boolean {
        return preferencesManager.isMembershipActive()
    }
    
    fun getPlanName(): String? {
        return preferencesManager.getMembershipPlanName()
    }
    
    fun getEndDate(): Long? {
        return preferencesManager.getMembershipEndDate()
    }
}
