package com.chronie.homemoney.domain.usecase

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import com.chronie.homemoney.R
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.domain.model.ExpenseType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject

/**
 * Exports expense records from the local database to an Excel (.xlsx) file
 * in the device's Downloads directory.
 *
 * The export supports optional date range filtering. The generated file includes
 * columns for date, expense type (localized), amount, and remarks.
 *
 * @param expenseDao Direct DAO access for efficient batch reading.
 * @param context Application context for resource strings and storage access.
 */
class ExportExpensesUseCase @Inject constructor(
    private val expenseDao: ExpenseDao,
    @param:ApplicationContext private val context: Context
) {
    
    /**
     * Exports expenses to an Excel file.
     *
     * If both [startDate] and [endDate] are provided, only expenses within
     * that range are exported. Otherwise, all expenses are exported.
     *
     * @param startDate Optional start of the export date range (inclusive).
     * @param endDate Optional end of the export date range (inclusive).
     * @return [Result.success] with the absolute file path on success,
     *         or [Result.failure] with a descriptive error.
     */
    suspend operator fun invoke(
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): Result<String> {
        return try {
            Log.d("ExportExpensesUseCase", "Starting export with startDate=$startDate, endDate=$endDate")
            
            // Fetch expenses: either filtered by date range or all records
            val expenses = if (startDate != null && endDate != null) {
                val startDateStr = startDate.toString()
                val endDateStr = endDate.toString()
                Log.d("ExportExpensesUseCase", "Querying expenses by date range: $startDateStr to $endDateStr")
                expenseDao.getExpensesByDateRangeSync(startDateStr, endDateStr)
                    .map { ExpenseMapper.toDomain(it) }
            } else {
                Log.d("ExportExpensesUseCase", "Querying all expenses")
                expenseDao.getAllExpenses().first()
                    .map { ExpenseMapper.toDomain(it) }
            }
            
            Log.d("ExportExpensesUseCase", "Found ${expenses.size} expenses to export")
            
            if (expenses.isEmpty()) {
                Log.w("ExportExpensesUseCase", "No expenses found")
                return Result.failure(Exception(context.getString(R.string.no_records_to_export)))
            }
            
            // Filter out invalid records (blank dates, negative amounts, empty types)
            Log.d("ExportExpensesUseCase", "Validating expenses data")
            val validExpenses = expenses.filter { expense ->
                val isValid = expense.date.isNotBlank() && 
                              expense.amount >= 0 &&
                              !expense.type.name.isBlank()
                if (!isValid) {
                    Log.w("ExportExpensesUseCase", "Skipping invalid expense: id=${expense.id}, date=${expense.date}, amount=${expense.amount}")
                }
                isValid
            }
            
            Log.d("ExportExpensesUseCase", "Valid expenses count: ${validExpenses.size}")
            
            if (validExpenses.isEmpty()) {
                return Result.failure(Exception("No valid records to export"))
            }
            
            // Ensure the Downloads directory exists
            Log.d("ExportExpensesUseCase", "Preparing Downloads directory")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                Log.d("ExportExpensesUseCase", "Creating Downloads directory")
                downloadsDir.mkdirs()
            }
            
            // Build filename with optional date range and timestamp
            val timestamp = System.currentTimeMillis()
            val dateRange = if (startDate != null && endDate != null) {
                "_${startDate}_${endDate}"
            } else {
                ""
            }
            val filename = "${context.getString(R.string.export_filename_prefix)}${dateRange}_$timestamp.xlsx"
            val file = File(downloadsDir, filename)
            
            Log.d("ExportExpensesUseCase", "Creating Excel file: ${file.absolutePath}")

            // Write the Excel workbook on the IO dispatcher
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { outputStream ->
                    Workbook(outputStream, "HomeMoney", "1.0").use { workbook ->
                        val sheet =
                            workbook.newWorksheet(context.getString(R.string.expense_records))

                        // Header row with localized column names
                        val headers = listOf(
                            context.getString(R.string.excel_header_date),
                            context.getString(R.string.excel_header_type),
                            context.getString(R.string.excel_header_amount),
                            context.getString(R.string.excel_header_remark)
                        )

                        headers.forEachIndexed { index, header ->
                            sheet.value(0, index, header)
                            sheet.style(0, index).bold().set()
                        }

                        // Write each expense as a data row
                        validExpenses.forEachIndexed { index, expense ->
                            val row = index + 1

                            if (index % 100 == 0) {
                                Log.d(
                                    "ExportExpensesUseCase",
                                    "Writing row $row/${validExpenses.size}, id=${expense.id}"
                                )
                            }

                            try {
                                val dateValue = expense.date.ifBlank { "" }
                                sheet.value(row, 0, dateValue)

                                // Resolve the localized name for the expense type
                                val typeValue = getExpenseTypeName(expense.type)
                                sheet.value(row, 1, typeValue)

                                // Sanitize NaN/Infinity amounts
                                val amountValue =
                                    if (expense.amount.isNaN() || expense.amount.isInfinite()) 0.0 else expense.amount
                                sheet.value(row, 2, amountValue)

                                val remarkValue = expense.remark?.ifBlank { "" } ?: ""
                                sheet.value(row, 3, remarkValue)
                            } catch (e: Exception) {
                                Log.e(
                                    "ExportExpensesUseCase",
                                    "Failed to write row $row: id=${expense.id}, date='${expense.date}', amount=${expense.amount}, type=${expense.type}",
                                    e
                                )
                                throw e
                            }
                        }

                        // Set reasonable column widths
                        Log.d("ExportExpensesUseCase", "Setting column widths")
                        sheet.width(0, 15.0)  // Date column
                        sheet.width(1, 12.0)  // Type column
                        sheet.width(2, 10.0)  // Amount column
                        sheet.width(3, 20.0)  // Remark column
                        Log.d(
                            "ExportExpensesUseCase",
                            "Column widths set successfully"
                        )
                    }
                }
            }
            
            Log.d("ExportExpensesUseCase", "Export completed successfully: ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("ExportExpensesUseCase", "Export failed", e)
            // Map common exceptions to user-friendly messages
            val errorMessage = when (e) {
                is java.security.AccessControlException -> "Storage permission denied"
                is java.io.FileNotFoundException -> "Storage directory not found"
                is java.io.IOException -> "File write error: ${e.message}"
                else -> e.message ?: "Unknown error"
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
    /**
     * Resolves the localized display name for an [ExpenseType] using Android resources.
     * Falls back to the enum name if the resource lookup fails.
     */
    @SuppressLint("DiscouragedApi")
    private fun getExpenseTypeName(type: ExpenseType): String {
        val resourceId = context.resources.getIdentifier(
            type.displayNameKey,
            "string",
            context.packageName
        )
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            type.name
        }
    }
}
