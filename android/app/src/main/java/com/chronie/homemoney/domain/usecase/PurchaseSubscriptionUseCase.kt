package com.chronie.homemoney.domain.usecase

import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.repository.MemberRepository
import javax.inject.Inject

class PurchaseSubscriptionUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(
        username: String,
        plan: SubscriptionPlan
    ): Result<Unit> {
        return try {
            // 生成一个简单的支付ID（实际应用中可能需要更复杂的逻辑）
            val paymentId = "PAYMENT_${System.currentTimeMillis()}"
            
            // 直接调用创建订阅 API
            val subscriptionResult = memberRepository.createSubscription(
                username = username,
                planId = plan.period,
                paymentId = paymentId
            )
            
            if (subscriptionResult.isFailure) {
                return Result.failure(
                    subscriptionResult.exceptionOrNull() ?: Exception("创建订阅失败")
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
