package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * AI 记录请求 DTO
 */
data class AIRecordRequest(
    val model: String,
    val messages: List<AIMessage>,
    val temperature: Double = 0.2,
    val stream: Boolean = false
)

/**
 * AI 消息
 */
data class AIMessage(
    val role: String,
    val content: Any // 可以是 String 或 List<AIMessageContent>
)

/**
 * AI 消息内容（用于多模态）
 */
data class AIMessageContent(
    val type: String, // "text" 或 "image_url"
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: AIImageUrl? = null
)

/**
 * AI 图片 URL
 */
data class AIImageUrl(
    val url: String
)

/**
 * AI 记录响应 DTO
 */
data class AIRecordResponse(
    val choices: List<AIChoice>
)

/**
 * AI 选择
 */
data class AIChoice(
    val message: AIResponseMessage
)

/**
 * AI 响应消息
 */
data class AIResponseMessage(
    val content: String
)

/**
 * AI 识别的支出记录 DTO
 */
data class AIExpenseRecordDto(
    val type: String,
    val amount: Double,
    val time: String,
    val remark: String
)
