package com.chronie.homemoney.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * AI Record Request DTO
 */
data class AIRecordRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<AIMessage>,
    @SerializedName("temperature")
    val temperature: Double = 0.2,
    @SerializedName("stream")
    val stream: Boolean = false
)

/**
 * AI Message
 */
data class AIMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: Any // Can be a String or List<AIMessageContent>
)

/**
 * AI Message Content (for multimodal input)
 */
data class AIMessageContent(
    @SerializedName("type")
    val type: String, // "text" or "image_url"
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: AIImageUrl? = null
)

/**
 * AI Image URL
 */
data class AIImageUrl(
    @SerializedName("url")
    val url: String
)

/**
 * AI Record Response DTO
 */
data class AIRecordResponse(
    @SerializedName("choices")
    val choices: List<AIChoice>
)

/**
 * AI Choice
 */
data class AIChoice(
    @SerializedName("message")
    val message: AIResponseMessage
)

/**
 * AI Response Message
 */
data class AIResponseMessage(
    @SerializedName("content")
    val content: String
)

/**
 * AI recognized Expense Record DTO
 */
data class AIExpenseRecordDto(
    @SerializedName("type")
    val type: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("date")
    val date: String,
    @SerializedName("remark")
    val remark: String
)
