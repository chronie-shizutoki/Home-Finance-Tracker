package com.chronie.homemoney.core.hybrid

/**
 * 功能迁移状态数据类
 */
data class FeatureMigrationStatus(
    val feature: Feature,
    val implementationType: ImplementationType,
    val isEnabled: Boolean = true,
    val migrationProgress: Int = 0, // 0-100
    val description: String = ""
)
