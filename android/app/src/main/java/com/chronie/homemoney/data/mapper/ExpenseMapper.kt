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
     * 数据库存储的是毫秒时间戳，转换为 LocalDateTime
     */
    fun toDomain(entity: ExpenseEntity): Expense {
        // 使用 UTC+8 (北京时区) 来解释时间戳
        val beijingZone = ZoneId.of("Asia/Shanghai")
        return Expense(
            id = entity.id,
            type = ExpenseType.fromString(entity.type),
            remark = entity.remark,
            amount = entity.amount,
            time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entity.time),
                beijingZone
            ),
            isSynced = entity.isSynced
        )
    }
    
    /**
     * Domain Model -> Entity
     * LocalDateTime 转换为毫秒时间戳存储
     */
    fun toEntity(expense: Expense): ExpenseEntity {
        val now = System.currentTimeMillis()
        // 将 LocalDateTime 视为北京时间，转换为时间戳
        val beijingZone = ZoneId.of("Asia/Shanghai")
        return ExpenseEntity(
            id = expense.id,
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            time = expense.time.atZone(beijingZone).toInstant().toEpochMilli(),
            createdAt = now,
            updatedAt = now,
            isSynced = expense.isSynced,
            serverId = null
        )
    }
    
    /**
     * DTO -> Domain Model
     * 后端存储的是 UTC 时间，需要转换为北京时间（UTC+8）
     */
    fun toDomain(dto: ExpenseDto): Expense {
        // 尝试解析ISO格式的日期时间，如果失败则尝试解析日期格式
        val time = try {
            // 解析 UTC 时间字符串，然后转换为北京时间
            // 例如：2025-11-14T16:00:00 (UTC) -> 2025-11-15T00:00:00 (北京时间)
            val utcZone = ZoneId.of("UTC")
            val beijingZone = ZoneId.of("Asia/Shanghai")
            
            // 解析为 UTC 时间
            val utcDateTime = LocalDateTime.parse(dto.time, DateTimeFormatter.ISO_DATE_TIME)
            
            // 转换为北京时间
            utcDateTime.atZone(utcZone).withZoneSameInstant(beijingZone).toLocalDateTime()
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
     * 直接发送用户选择的日期时间，不进行时区转换
     * 后端会使用 dayjs 自动处理时区转换
     */
    fun toDto(expense: Expense): ExpenseDto {
        val formatter = DateTimeFormatter.ISO_DATE_TIME
        // 直接使用用户选择的时间，不进行转换
        // 例如：2025-11-15 00:00:00 -> 2025-11-15T00:00:00
        
        return ExpenseDto(
            id = expense.id.toLongOrNull(),
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            time = expense.time.format(formatter),
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
