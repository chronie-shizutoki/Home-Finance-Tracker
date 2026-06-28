package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.remote.dto.AIExpenseRecordDto
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * AI Record Data Mapper
 */
object AIRecordMapper {
    
    /**
     * DTO -> Domain Model
     */
    fun toDomain(dto: AIExpenseRecordDto): AIExpenseRecord {
        return AIExpenseRecord(
            type = parseExpenseType(dto.type),
            amount = dto.amount,
            date = dto.date, // Direct use of date string
            remark = dto.remark,
            isEdited = false,
            isValid = validateRecord(dto)
        )
    }
    
    /**
     * Parse expense type from string
     */
    private fun parseExpenseType(typeStr: String): ExpenseType {
        return when (typeStr) {
            "日常用品" -> ExpenseType.DAILY_GOODS
            "奢侈品" -> ExpenseType.LUXURY
            "通讯费用" -> ExpenseType.COMMUNICATION
            "食品" -> ExpenseType.FOOD
            "零食糖果" -> ExpenseType.SNACKS
            "冷饮" -> ExpenseType.COLD_DRINKS
            "方便食品" -> ExpenseType.CONVENIENCE_FOOD
            "纺织品" -> ExpenseType.TEXTILES
            "饮品" -> ExpenseType.BEVERAGES
            "调味品" -> ExpenseType.CONDIMENTS
            "交通出行" -> ExpenseType.TRANSPORTATION
            "餐饮" -> ExpenseType.DINING
            "医疗费用" -> ExpenseType.MEDICAL
            "水果" -> ExpenseType.FRUITS
            "水产品" -> ExpenseType.SEAFOOD
            "乳制品" -> ExpenseType.DAIRY
            "礼物人情" -> ExpenseType.GIFTS
            "旅行度假" -> ExpenseType.TRAVEL
            "政务" -> ExpenseType.GOVERNMENT
            "水电煤气" -> ExpenseType.UTILITIES
            else -> ExpenseType.OTHER
        }
    }
    
    /**
     * Parse date time from string
     */
    private fun parseDateTime(dateStr: String): LocalDateTime {
        return try {
            // Try to parse ISO format
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            try {
                // Try to parse date format
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)
                date.atStartOfDay()
            } catch (e2: Exception) {
                // Default to current time if parsing fails
                LocalDateTime.now()
            }
        }
    }
    
    /**
     * Validate record validity
     */
    private fun validateRecord(dto: AIExpenseRecordDto): Boolean {
        return dto.amount > 0 && dto.remark.isNotBlank()
    }
}
