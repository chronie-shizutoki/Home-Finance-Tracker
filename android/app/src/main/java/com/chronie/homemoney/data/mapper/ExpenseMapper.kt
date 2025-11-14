package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 支出数据映射器
 */
object ExpenseMapper {
    
    /**
     * Entity -> Domain Model
     */
    fun toDomain(entity: ExpenseEntity): Expense {
        return Expense(
            id = entity.id,
            type = ExpenseType.fromString(entity.type),
            remark = entity.remark,
            amount = entity.amount,
            time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entity.time),
                ZoneId.systemDefault()
            ),
            isSynced = entity.isSynced
        )
    }
    
    /**
     * Domain Model -> Entity
     */
    fun toEntity(expense: Expense): ExpenseEntity {
        val now = System.currentTimeMillis()
        return ExpenseEntity(
            id = expense.id,
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            time = expense.time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            createdAt = now,
            updatedAt = now,
            isSynced = expense.isSynced,
            serverId = null
        )
    }
    
    /**
     * DTO -> Domain Model
     * API返回的是UTC时间，需要加8小时转换为北京时间
     */
    fun toDomain(dto: ExpenseDto): Expense {
        // 尝试解析ISO格式的日期时间，如果失败则尝试解析日期格式
        val time = try {
            // 解析UTC时间并加8小时
            val utcTime = LocalDateTime.parse(dto.time, DateTimeFormatter.ISO_DATE_TIME)
            utcTime.plusHours(8)
        } catch (e: Exception) {
            try {
                // 尝试解析 YYYY-MM-DD 格式
                val date = java.time.LocalDate.parse(dto.time, DateTimeFormatter.ISO_DATE)
                date.atStartOfDay()
            } catch (e2: Exception) {
                LocalDateTime.now()
            }
        }
        
        return Expense(
            id = dto.id?.toString() ?: "",
            type = ExpenseType.fromString(dto.type),
            remark = dto.remark,
            amount = dto.amount,
            time = time,
            isSynced = true
        )
    }
    
    /**
     * Domain Model -> DTO
     * 发送给API时需要减8小时转换为UTC时间
     */
    fun toDto(expense: Expense): ExpenseDto {
        val formatter = DateTimeFormatter.ISO_DATE_TIME
        // 减8小时转换为UTC时间
        val utcTime = expense.time.minusHours(8)
        return ExpenseDto(
            id = expense.id.toLongOrNull(),
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            time = utcTime.format(formatter),
            createdAt = null,
            updatedAt = null
        )
    }
    
    /**
     * 获取支出类型的中文名称
     */
    private fun getChineseTypeName(type: ExpenseType): String {
        return when (type) {
            ExpenseType.DAILY_GOODS -> "日常用品"
            ExpenseType.LUXURY -> "奢侈品"
            ExpenseType.COMMUNICATION -> "通讯费用"
            ExpenseType.FOOD -> "食品"
            ExpenseType.SNACKS -> "零食糖果"
            ExpenseType.COLD_DRINKS -> "冷饮"
            ExpenseType.CONVENIENCE_FOOD -> "方便食品"
            ExpenseType.TEXTILES -> "纺织品"
            ExpenseType.BEVERAGES -> "饮品"
            ExpenseType.CONDIMENTS -> "调味品"
            ExpenseType.TRANSPORTATION -> "交通出行"
            ExpenseType.DINING -> "餐饮"
            ExpenseType.MEDICAL -> "医疗费用"
            ExpenseType.FRUITS -> "水果"
            ExpenseType.OTHER -> "其他"
            ExpenseType.SEAFOOD -> "水产品"
            ExpenseType.DAIRY -> "乳制品"
            ExpenseType.GIFTS -> "礼物人情"
            ExpenseType.TRAVEL -> "旅行度假"
            ExpenseType.GOVERNMENT -> "政务"
            ExpenseType.UTILITIES -> "水电煤气"
        }
    }
}
