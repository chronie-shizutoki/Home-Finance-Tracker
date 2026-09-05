package com.chronie.homemoney.domain.repository

import android.net.Uri
import com.chronie.homemoney.domain.model.AIExpenseRecord

/**
 * AI Record Repository Interface
 *
 * Recognition pipeline (fully on-device):
 *   image -> OpenCV document scan -> Qwen3-VL multimodal LLM -> structured records
 *   text  -> Qwen3-VL LLM -> structured records
 */
interface AIRecordRepository {

    /**
     * Parse text to AI expense records using the on-device LLM
     */
    suspend fun parseTextToRecords(text: String): Result<List<AIExpenseRecord>>

    /**
     * Parse images to expense records:
     * document-scan each image with OpenCV, then feed the cleaned pages
     * to the on-device multimodal LLM in a single request.
     */
    suspend fun parseImagesToRecords(imageUris: List<Uri>): Result<List<AIExpenseRecord>>

    /**
     * Batch Save AI Recognized Records
     */
    suspend fun saveRecords(records: List<AIExpenseRecord>): Result<Unit>
}
