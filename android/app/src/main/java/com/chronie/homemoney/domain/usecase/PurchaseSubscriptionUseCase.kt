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
            android.util.Log.d("PurchaseSubscription", "开始购买流程: username=$username, plan=${plan.name}")
            
            // 步骤1: 先调用支付API，向第三方支付提供商发起支付
            android.util.Log.d("PurchaseSubscription", "步骤1: 调用支付API")
            val paymentResult = memberRepository.subscribePayment(
                username = username,
                planId = plan.period
            )
            
            if (paymentResult.isFailure) {
                val error = paymentResult.exceptionOrNull()
                android.util.Log.e("PurchaseSubscription", "支付失败", error)
                return Result.failure(error ?: Exception("支付失败"))
            }
            
            // 获取支付返回的订单ID
            val orderId = paymentResult.getOrNull()
            android.util.Log.d("PurchaseSubscription", "支付成功，订单ID: $orderId")
            
            if (orderId == null) {
                android.util.Log.e("PurchaseSubscription", "未获取到支付订单ID")
                return Result.failure(Exception("未获取到支付订单ID"))
            }
            
            // 步骤2: 使用支付订单ID创建订阅记录
            android.util.Log.d("PurchaseSubscription", "步骤2: 创建订阅记录，使用订单ID: $orderId")
            val subscriptionResult = memberRepository.createSubscription(
                username = username,
                planId = plan.period,
                paymentId = orderId
            )
            
            if (subscriptionResult.isFailure) {
                val error = subscriptionResult.exceptionOrNull()
                android.util.Log.e("PurchaseSubscription", "创建订阅失败", error)
                return Result.failure(error ?: Exception("创建订阅失败"))
            }
            
            android.util.Log.d("PurchaseSubscription", "购买流程完成")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("PurchaseSubscription", "购买流程异常", e)
            Result.failure(e)
        }
    }
}
