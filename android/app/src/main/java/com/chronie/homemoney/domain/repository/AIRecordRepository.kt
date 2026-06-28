package com.chronie.homemoney.domain.repository

import android.net.Uri
import com.chronie.homemoney.domain.model.AIExpenseRecord

/**
 * AI Record Repository Interface
 */
interface AIRecordRepository {
    
    /**
     * Parse text to AI expense records
     */
    suspend fun parseTextToRecords(text: String): Result<List<AIExpenseRecord>>
    
    /**
     * Parse Images to Expense Records
     */
    suspend fun parseImagesToRecords(imageUris: List<Uri>): Result<List<AIExpenseRecord>>
    
    /**
     * Batch Save AI Recognized Records
     */
    suspend fun saveRecords(records: List<AIExpenseRecord>): Result<Unit>
}
