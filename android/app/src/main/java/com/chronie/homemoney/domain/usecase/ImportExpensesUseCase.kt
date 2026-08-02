package com.chronie.homemoney.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Cell

import java.util.*
import javax.inject.Inject

/**
 * Imports expense records from an Excel (.xlsx) file into the local database.
 *
 * Supports multi-language headers (Chinese, English) and automatically maps
 * expense type names to the corresponding [ExpenseType] enum values.
 *
 * @param expenseRepository Repository for persisting imported expenses.
 * @param context Application context for content resolver access and string resources.
 */
class ImportExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    @param:ApplicationContext private val context: Context
) {
    
    /**
     * Result summary after completing an import operation.
     *
     * @property successCount Number of expenses successfully imported.
     * @property failedCount Number of expenses that failed to import.
     * @property errors Human-readable error messages for failed rows.
     */
    data class ImportResult(
        val successCount: Int,
        val failedCount: Int,
        val errors: List<String>
    )
    
    /**
     * Imports expenses from the Excel file at the given [Uri].
     *
     * The file must contain columns for date, type, and amount at minimum.
     * An optional remark column is also supported.
     *
     * @param uri Content URI pointing to the Excel file.
     * @return [Result.success] with an [ImportResult] summary on successful parse,
     *         or [Result.failure] if the file cannot be read or is empty.
     */
    suspend operator fun invoke(uri: Uri): Result<ImportResult> {
        return try {
            val expenses = parseExcelFile(uri)
            
            if (expenses.isEmpty()) {
                return Result.failure(Exception("No valid records found in file"))
            }
            
            // Import each parsed record into the repository
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            expenses.forEachIndexed { index, expense ->
                val result = expenseRepository.addExpense(expense)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failedCount++
                    // Row index + 2 because row 1 is the header and index is 0-based
                    errors.add("Row ${index + 2}: ${result.exceptionOrNull()?.message}")
                }
            }
            
            Result.success(ImportResult(successCount, failedCount, errors))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parses an Excel file from the given [Uri] into a list of [Expense] domain objects.
     *
     * Automatically detects column positions from header labels (supports Chinese and English).
     * Skips rows with unparseable dates, missing amounts, or invalid expense types.
     */
    private fun parseExcelFile(uri: Uri): List<Expense> {
        val expenses = mutableListOf<Expense>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ReadableWorkbook(inputStream).use { workbook ->
                val sheet = workbook.firstSheet
                
                val rows = sheet.read()
                if (rows.isEmpty()) {
                    throw Exception("Empty file")
                }
                
                // Parse header row to locate columns by name
                val headerRow = rows[0]
                val headers = mutableMapOf<String, Int>()
                
                headerRow.forEachIndexed { index, cell ->
                    val cellValue = cell.asString()
                    if (cellValue.isNotEmpty()) {
                        headers[cellValue.trim()] = index
                    }
                }
                
                // Find column indices for required and optional columns
                val dateColIndex = findColumnIndex(headers, "date")
                val typeColIndex = findColumnIndex(headers, "type")
                val amountColIndex = findColumnIndex(headers, "amount")
                val remarkColIndex = findColumnIndex(headers, "remark")
                
                // Date, type, and amount are mandatory
                if (dateColIndex == -1 || typeColIndex == -1 || amountColIndex == -1) {
                    throw Exception(context.getString(R.string.import_validation_error, "Missing required columns"))
                }
                
                // Parse each data row (skip header at row 0)
                for (i in 1 until rows.size) {
                    val row = rows[i]
                    
                    try {
                        val dateCell = row.getCell(dateColIndex)
                        val dateStr = parseDateCell(dateCell) ?: continue
                        
                        val typeCell = row.getCell(typeColIndex)
                        val typeStr = typeCell?.asString() ?: continue
                        val expenseType = parseExpenseType(typeStr)
                        
                        val amountCell = row.getCell(amountColIndex)
                        val amount = parseAmountCell(amountCell) ?: continue
                        
                        // Skip zero or negative amounts
                        if (amount <= 0) continue
                        
                        val remarkCell = row.getCell(remarkColIndex)
                        val remark = remarkCell?.asString()
                        
                        val expense = Expense(
                            id = UUID.randomUUID().toString(),
                            type = expenseType,
                            remark = remark,
                            amount = amount,
                            date = dateStr,
                            isSynced = false
                        )
                        
                        expenses.add(expense)
                    } catch (e: Exception) {
                        android.util.Log.w("ImportExpenses", "Failed to parse row ${i + 1}: ${e.message}")
                    }
                }
            }
        }
        
        return expenses
    }
    
    /**
     * Parses a cell value as a date string in "YYYY-MM-DD" format.
     * Handles both date-only strings and datetime strings with time components.
     * Returns null if the cell is empty or the value cannot be parsed as a date.
     */
    private fun parseDateCell(cell: Cell?): String? {
        if (cell == null) return null
        
        return try {
            val cellValue = cell.asString()
            // Strip time portion if present (e.g., "2024-01-15T12:00:00" -> "2024-01-15")
            val datePart = if (cellValue.contains('T') || cellValue.contains(' ')) {
                cellValue.substringBefore('T').substringBefore(' ')
            } else {
                cellValue
            }
            
            java.time.LocalDate.parse(datePart).toString()
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Parses a cell value as a double amount.
     * Tries numeric parsing first, then falls back to string-to-number parsing.
     * Returns null if the cell is empty or the value is unparseable.
     */
    private fun parseAmountCell(cell: Cell?): Double? {
        if (cell == null) return null
        
        return try {
            cell.asNumber().toDouble()
        } catch (_: Exception) {
            cell.asString().toDoubleOrNull()
        }
    }
    
    /**
     * Finds the column index for a given logical column key by matching
     * against multiple possible header labels in different languages (Chinese, English).
     *
     * @param headers Map of header label to column index.
     * @param columnKey Logical key ("date", "type", "amount", "remark").
     * @return The 0-based column index, or -1 if not found.
     */
    private fun findColumnIndex(headers: Map<String, Int>, columnKey: String): Int {
        val possibleHeaders = when (columnKey) {
            "date" -> listOf(
                context.getString(R.string.excel_header_date),
                "Date", "日期", "日期"
            )
            "type" -> listOf(
                context.getString(R.string.excel_header_type),
                "Type", "类型", "類型"
            )
            "amount" -> listOf(
                context.getString(R.string.excel_header_amount),
                "Amount", "金额", "金額"
            )
            "remark" -> listOf(
                context.getString(R.string.excel_header_remark),
                "Remark", "备注", "備註"
            )
            else -> emptyList()
        }
        
        for (header in possibleHeaders) {
            val index = headers[header]
            if (index != null) return index
        }
        
        return -1
    }
    
    /**
     * Parses an expense type string into the corresponding [ExpenseType] enum value.
     * First tries to match against localized display names using Android resources,
     * then falls back to [ExpenseType.fromString] for Chinese name matching.
     */
    @SuppressLint("DiscouragedApi")
    private fun parseExpenseType(typeStr: String): ExpenseType {
        return ExpenseType.entries.find { type ->
            val resourceId = context.resources.getIdentifier(
                type.displayNameKey,
                "string",
                context.packageName
            )
            if (resourceId != 0) {
                context.getString(resourceId) == typeStr
            } else {
                false
            }
        } ?: ExpenseType.fromString(typeStr)
    }
}
