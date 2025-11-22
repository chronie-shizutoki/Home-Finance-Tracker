package com.chronie.homemoney.domain.model

/**
 * 支出领域模型
 */
data class Expense(
    val id: String,
    val type: ExpenseType,
    val remark: String?,
    val amount: Double,
    val date: String,
    val isSynced: Boolean = false
)

/**
 * 支出类型枚举
 */
enum class ExpenseType(val displayNameKey: String) {
    DAILY_GOODS("expense_type_daily_goods"),           // 日常用品
    LUXURY("expense_type_luxury"),                     // 奢侈品
    COMMUNICATION("expense_type_communication"),       // 通讯费用
    FOOD("expense_type_food"),                         // 食品
    SNACKS("expense_type_snacks"),                     // 零食糖果
    COLD_DRINKS("expense_type_cold_drinks"),           // 冷饮
    CONVENIENCE_FOOD("expense_type_convenience_food"), // 方便食品
    TEXTILES("expense_type_textiles"),                 // 纺织品
    BEVERAGES("expense_type_beverages"),               // 饮品
    CONDIMENTS("expense_type_condiments"),             // 调味品
    TRANSPORTATION("expense_type_transportation"),     // 交通出行
    DINING("expense_type_dining"),                     // 餐饮
    MEDICAL("expense_type_medical"),                   // 医疗费用
    FRUITS("expense_type_fruits"),                     // 水果
    OTHER("expense_type_other"),                       // 其他
    SEAFOOD("expense_type_seafood"),                   // 水产品
    DAIRY("expense_type_dairy"),                       // 乳制品
    GIFTS("expense_type_gifts"),                       // 礼物人情
    TRAVEL("expense_type_travel"),                     // 旅行度假
    GOVERNMENT("expense_type_government"),             // 政务
    UTILITIES("expense_type_utilities");               // 水电煤气

    companion object {
        fun fromString(value: String): ExpenseType {
            return when (value) {
                "日常用品" -> DAILY_GOODS
                "奢侈品" -> LUXURY
                "通讯费用" -> COMMUNICATION
                "食品" -> FOOD
                "零食糖果" -> SNACKS
                "冷饮" -> COLD_DRINKS
                "方便食品" -> CONVENIENCE_FOOD
                "纺织品" -> TEXTILES
                "饮品" -> BEVERAGES
                "调味品" -> CONDIMENTS
                "交通出行" -> TRANSPORTATION
                "餐饮" -> DINING
                "医疗费用" -> MEDICAL
                "水果" -> FRUITS
                "其他" -> OTHER
                "水产品" -> SEAFOOD
                "乳制品" -> DAIRY
                "礼物人情" -> GIFTS
                "旅行度假" -> TRAVEL
                "政务" -> GOVERNMENT
                "水电煤气" -> UTILITIES
                else -> OTHER
            }
        }
    }
}
