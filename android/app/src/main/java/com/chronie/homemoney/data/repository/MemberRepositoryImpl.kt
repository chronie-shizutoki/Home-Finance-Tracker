package com.chronie.homemoney.data.repository

import com.chronie.homemoney.data.mapper.MemberMapper
import com.chronie.homemoney.data.mapper.SubscriptionMapper
import com.chronie.homemoney.data.remote.api.MemberApi
import com.chronie.homemoney.data.remote.dto.MemberRequest
import com.chronie.homemoney.domain.model.Member
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus
import com.chronie.homemoney.domain.repository.MemberRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val memberApi: MemberApi
) : MemberRepository {

    override suspend fun getOrCreateMember(username: String): Result<Member> {
        return try {
            val response = memberApi.getOrCreateMember(MemberRequest(username))
            if (response.success && response.data != null) {
                Result.success(MemberMapper.toDomain(response.data))
            } else {
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemberInfo(username: String): Result<Member> {
        return try {
            val response = memberApi.getMemberInfo(username)
            if (response.success && response.data != null) {
                Result.success(MemberMapper.toDomain(response.data))
            } else {
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentSubscription(username: String): Result<SubscriptionStatus> {
        return try {
            val response = memberApi.getCurrentSubscription(username)
            if (response.success) {
                Result.success(SubscriptionMapper.toSubscriptionStatus(response.data))
            } else {
                // 如果没有订阅，返回一个非活跃状态
                Result.success(SubscriptionMapper.toSubscriptionStatus(null))
            }
        } catch (e: Exception) {
            // 网络错误时，返回失败
            Result.failure(e)
        }
    }
    
    override suspend fun getSubscriptionPlans(): Result<List<SubscriptionPlan>> {
        return try {
            val response = memberApi.getSubscriptionPlans()
            if (response.success && response.data != null) {
                Result.success(response.data.map { SubscriptionMapper.toDomain(it) })
            } else {
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
