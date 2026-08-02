package com.chronie.homemoney.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around Google ML Kit's on-device text recognition.
 *
 * Supports Latin, Chinese, Japanese, and Korean scripts. Automatically detects
 * the device locale to select an appropriate language model via [getAutoDetectedLanguage],
 * or accepts an explicit [OcrLanguage].
 *
 * Recognition strategy:
 * 1. Attempt OCR on the original (scaled) image.
 * 2. If the recognized text is too short (fewer than [MIN_EFFECTIVE_TEXT_LENGTH]
 *    non-whitespace characters), preprocess the image with [OpenCVImageProcessor]
 *    (CLAHE contrast enhancement + denoising) and retry.
 * 3. Return the longer result.
 *
 * Recognizers are cached per language in a [ConcurrentHashMap] to avoid
 * repeated client construction.
 */
@Singleton
class OcrHelper @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        private const val TAG = "OcrHelper"
        private const val MIN_TEXT_LENGTH = 1
        private const val LINE_MERGE_THRESHOLD = 20
        private const val MIN_EFFECTIVE_TEXT_LENGTH = 10
    }

    enum class OcrLanguage(val code: String) {
        LATIN("latin"),
        CHINESE("zh"),
        JAPANESE("ja"),
        KOREAN("ko")
    }

    private val recognizers = ConcurrentHashMap<OcrLanguage, com.google.mlkit.vision.text.TextRecognizer>()

    private fun getRecognizer(language: OcrLanguage): com.google.mlkit.vision.text.TextRecognizer {
        return recognizers.getOrPut(language) {
            when (language) {
                OcrLanguage.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                OcrLanguage.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                OcrLanguage.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                OcrLanguage.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    fun getAutoDetectedLanguage(): OcrLanguage {
        val locale = context.resources.configuration.locales[0]
        val languageCode = locale.language

        return when (languageCode) {
            "zh" -> OcrLanguage.CHINESE
            "ja" -> OcrLanguage.JAPANESE
            "ko" -> OcrLanguage.KOREAN
            else -> OcrLanguage.LATIN
        }
    }

    suspend fun recognizeTextFromUri(uri: Uri, language: OcrLanguage = getAutoDetectedLanguage()): String {
        return withContext(Dispatchers.IO) {
            try {
                val processor = OpenCVImageProcessor(context)
                
                val originalBitmap = loadBitmap(uri)
                if (originalBitmap == null) {
                    Log.e(TAG, "Failed to load image")
                    return@withContext ""
                }

                val scaledBitmap = processor.scaleBitmap(originalBitmap)

                val rawResult = recognizeTextFromBitmapInternal(scaledBitmap, language, "original")
                if (isResultGoodEnough(rawResult)) {
                    Log.d(TAG, "Using original recognition result (length=${rawResult.length})")
                    return@withContext rawResult
                }

                Log.d(TAG, "Original result too short (length=${rawResult.length}), trying preprocessed")
                val preprocessedBitmap = processor.processBitmap(scaledBitmap) ?: scaledBitmap
                val preprocessedResult = recognizeTextFromBitmapInternal(preprocessedBitmap, language, "preprocessed")
                
                if (preprocessedResult.length > rawResult.length) {
                    Log.d(TAG, "Using preprocessed result (length=${preprocessedResult.length})")
                    preprocessedResult
                } else {
                    Log.d(TAG, "Using original result (length=${rawResult.length})")
                    rawResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recognizing text from URI", e)
                ""
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
            null
        }
    }

    private fun isResultGoodEnough(result: String): Boolean {
        if (result.isBlank()) return false
        val nonSpaceChars = result.filter { !it.isWhitespace() }
        return nonSpaceChars.length >= MIN_EFFECTIVE_TEXT_LENGTH
    }

    private suspend fun recognizeTextFromBitmapInternal(
        bitmap: Bitmap, 
        language: OcrLanguage,
        source: String
    ): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = getRecognizer(language)
            val startTime = System.currentTimeMillis()
            val result = recognizer.process(image).await()
            val elapsed = System.currentTimeMillis() - startTime
            val text = extractSortedAndMergedText(result)
            Log.d(TAG, "OCR [$source] completed in ${elapsed}ms, result length=${text.length}")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text from bitmap [$source]", e)
            ""
        }
    }

    private fun extractSortedAndMergedText(result: Text): String {
        val allLines = mutableListOf<LineWithPosition>()
        
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox
                if (boundingBox != null) {
                    val text = line.text.trim()
                    if (text.length >= MIN_TEXT_LENGTH) {
                        allLines.add(
                            LineWithPosition(
                                text = text,
                                top = boundingBox.top,
                                bottom = boundingBox.bottom,
                                left = boundingBox.left,
                                right = boundingBox.right,
                                centerY = (boundingBox.top + boundingBox.bottom) / 2
                            )
                        )
                    }
                }
            }
        }

        if (allLines.isEmpty()) {
            return result.text
        }

        allLines.sortWith(compareBy({ it.top }, { it.left }))

        val mergedLines = mutableListOf<LineWithPosition>()
        for (line in allLines) {
            if (mergedLines.isEmpty()) {
                mergedLines.add(line)
            } else {
                val lastLine = mergedLines.last()
                if (Math.abs(line.centerY - lastLine.centerY) <= LINE_MERGE_THRESHOLD) {
                    val mergedText = if (line.left > lastLine.right + 5) {
                        "${lastLine.text} ${line.text}"
                    } else {
                        "${lastLine.text}${line.text}"
                    }
                    mergedLines.removeLast()
                    mergedLines.add(
                        LineWithPosition(
                            text = mergedText,
                            top = minOf(lastLine.top, line.top),
                            bottom = maxOf(lastLine.bottom, line.bottom),
                            left = minOf(lastLine.left, line.left),
                            right = maxOf(lastLine.right, line.right),
                            centerY = (minOf(lastLine.top, line.top) + maxOf(lastLine.bottom, line.bottom)) / 2
                        )
                    )
                } else {
                    mergedLines.add(line)
                }
            }
        }

        return mergedLines.joinToString("\n") { it.text }
    }

    private data class LineWithPosition(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
        val centerY: Int
    )
}
