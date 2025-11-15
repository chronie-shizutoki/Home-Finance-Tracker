package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * 获取可用会员套餐列表的UseCase
 */
class GetSubscriptionPlansUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(): Result<List<SubscriptionPlan>> {
        return try {
            memberRepository.getSubscriptionPlans()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
