package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.remote.dto.AIExpenseRecordDto
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mapper for converting AI-recognized expense records from the server DTO
 * to the domain model.
 *
 * AI records come from the OCR/recognition pipeline and may need user
 * validation before being saved as regular expenses.
 */
object AIRecordMapper {
    
    /**
     * Converts an AI expense record DTO to a domain model.
     *
     * The resulting record is marked as not yet edited ([isEdited] = false)
     * and its validity is determined by whether the amount is positive
     * and the remark is non-empty.
     *
     * @param dto The raw AI recognition result from the server.
     * @return An [AIExpenseRecord] ready for user review and editing.
     */
    fun toDomain(dto: AIExpenseRecordDto): AIExpenseRecord {
        return AIExpenseRecord(
            type = parseExpenseType(dto.type),
            amount = dto.amount,
            date = dto.date,
            remark = dto.remark,
            isEdited = false,
            isValid = validateRecord(dto)
        )
    }
    
    /**
     * Maps a Chinese type name string to an [ExpenseType] enum.
     * Falls back to [ExpenseType.OTHER] for unrecognized types.
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
     * Parses a date/datetime string into a [LocalDateTime].
     * Tries ISO datetime format first, then ISO date format,
     * falling back to the current time if all parsing fails.
     */
    private fun parseDateTime(dateStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)
                date.atStartOfDay()
            } catch (e2: Exception) {
                LocalDateTime.now()
            }
        }
    }
    
    /**
     * Validates whether an AI-recognized record is usable.
     * A valid record must have a positive amount and a non-blank remark.
     */
    private fun validateRecord(dto: AIExpenseRecordDto): Boolean {
        return dto.amount > 0 && dto.remark.isNotBlank()
    }
}
