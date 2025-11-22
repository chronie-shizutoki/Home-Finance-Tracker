package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType

/**
 * 支出数据映射器
 */
object ExpenseMapper {
    
    /**
     * Entity -> Domain Model
     * 直接使用数据库中的日期字符串
     */
    fun toDomain(entity: ExpenseEntity): Expense {
        return Expense(
            id = entity.id,
            type = ExpenseType.fromString(entity.type),
            remark = entity.remark,
            amount = entity.amount,
            date = entity.date,
            isSynced = entity.isSynced
        )
    }
    
    /**
     * Domain Model -> Entity
     * 日期字符串转换为毫秒时间戳存储
     */
    fun toEntity(expense: Expense): ExpenseEntity {
        val now = System.currentTimeMillis()
        // 将日期字符串转换为时间戳（当天0点）
        val date = java.time.LocalDate.parse(expense.date)
        val timeInMillis = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        return ExpenseEntity(
            id = expense.id,
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            date = expense.date,
            isSynced = expense.isSynced,
            serverId = null
        )
    }
    
    /**
     * DTO -> Domain Model
     * 直接使用后端的date字段或time字段作为日期字符串
     */
    fun toDomain(dto: ExpenseDto): Expense {
        // 直接使用dto中的date字段作为日期字符串，如果不是YYYY-MM-DD格式则取今天
        val dateStr = try {
            // 尝试检查是否是日期时间格式，如果是则提取日期部分
            if (dto.date.contains('T') || dto.date.contains(' ')) {
                val datePart = dto.date.substringBefore('T').substringBefore(' ')
                // 验证是否是有效的YYYY-MM-DD格式
                java.time.LocalDate.parse(datePart)
                datePart
            } else {
                // 尝试直接解析为日期
                java.time.LocalDate.parse(dto.date)
                dto.date
            }
        } catch (e: Exception) {
            // 如果解析失败，使用今天的日期
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        
        return Expense(
            id = dto.id?.toString() ?: "",
            type = ExpenseType.fromString(dto.type),
            remark = dto.remark,
            amount = dto.amount,
            date = dateStr,
            isSynced = true
        )
    }
    
    /**
     * Domain Model -> DTO
     * 直接使用日期字符串，不进行复杂的时间转换
     */
    fun toDto(expense: Expense): ExpenseDto {
        // 直接使用日期字符串
        return ExpenseDto(
            id = expense.id.toLongOrNull(),
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            date = expense.date // 后端会处理日期字段
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
