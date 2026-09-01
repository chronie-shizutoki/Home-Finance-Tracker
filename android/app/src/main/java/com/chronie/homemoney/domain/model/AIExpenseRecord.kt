package com.chronie.homemoney.domain.model

/**
 * An expense record extracted from an AI-powered OCR scan (e.g., a receipt photo).
 *
 * These records may be edited by the user before being finalized and saved
 * as regular [Expense] entries. The [isEdited] and [isValid] flags track
 * whether the user has made corrections and whether the AI result is usable.
 *
 * @property id Unique identifier (maybe empty before saving).
 * @property type The expense category detected/assigned by AI.
 * @property amount The monetary amount extracted from the receipt.
 * @property date The transaction date in "YYYY-MM-DD" format.
 * @property remark OCR-extracted description or merchant name.
 * @property isEdited Whether the user has manually modified any field after AI recognition.
 * @property isValid Whether the AI result passes validation (e.g., positive amount, valid date).
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
     * Converts this AI record into a regular [Expense] domain model,
     * marking it as unsynced so it will be pushed to the server on next sync.
     *
     * @return A new [Expense] instance populated from this AI record.
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
