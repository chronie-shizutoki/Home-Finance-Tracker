package com.chronie.homemoney.domain.model

data class Member(
    val id: String,
    val username: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
