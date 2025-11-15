package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.remote.dto.SubscriptionPlanDto
import com.chronie.homemoney.data.remote.dto.UserSubscriptionDto
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus
import java.time.Instant
import java.time.ZoneId

object SubscriptionMapper {
    
    fun toSubscriptionStatus(dto: UserSubscriptionDto?): SubscriptionStatus {
        if (dto == null) {
            return SubscriptionStatus(
                isActive = false,
                planName = null,
                planDescription = null,
                startDate = null,
                endDate = null,
                status = null,
                autoRenew = false
            )
        }
        
        return SubscriptionStatus(
            isActive = dto.status == "active" && isDateValid(dto.endDate),
            planName = dto.subscriptionPlan?.name,
            planDescription = dto.subscriptionPlan?.description,
            startDate = parseDate(dto.startDate),
            endDate = parseDate(dto.endDate),
            status = dto.status,
            autoRenew = dto.autoRenew
        )
    }
    
    fun toDomain(dto: SubscriptionPlanDto): SubscriptionPlan {
        return SubscriptionPlan(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            duration = dto.duration,
            price = dto.price,
            period = dto.period,
            isActive = dto.isActive
        )
    }
    
    private fun parseDate(dateString: String): Long? {
        return try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isDateValid(endDateString: String): Boolean {
        val endDate = parseDate(endDateString) ?: return false
        return System.currentTimeMillis() < endDate
    }
}
