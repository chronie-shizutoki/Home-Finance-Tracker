package com.chronie.homemoney.domain.model

import java.time.LocalDateTime

/**
 * AI recognized Expense Record
 */
data class AIExpenseRecord(
    val id: String = "",
    val type: ExpenseType,
    val amount: Double,
    val date: String,
    val remark: String,
    val isEdited: Boolean = false,
    val isValid: Boolean = true
) {
    /**
     * Convert to normal expense record
     */
    fun toExpense(): Expense {
        return Expense(
            id = id,
            type = type,
            amount = amount,
            date = date,
            remark = remark,
            isSynced = false
        )
    }
}
