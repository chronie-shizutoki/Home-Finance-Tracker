package com.chronie.homemoney.domain.model

/**
 * Core expense/transaction domain model.
 *
 * Represents a single financial transaction with support for soft-deletion
 * and optimistic sync tracking.
 *
 * @property id Unique identifier for this expense record.
 * @property type The category/type of this expense (e.g., FOOD, TRANSPORTATION).
 * @property remark Optional user-provided note or description.
 * @property amount The monetary amount of the transaction.
 * @property date The date of the transaction in "YYYY-MM-DD" format.
 * @property version Optimistic locking version for conflict resolution during sync.
 * @property updatedAt Epoch millis timestamp of the last modification.
 * @property deletedAt Non-null when the record has been soft-deleted (epoch millis), null otherwise.
 * @property isSynced Whether this record has been synchronized with the server.
 */
data class Expense(
    val id: String,
    val type: ExpenseType,
    val remark: String?,
    val amount: Double,
    val date: String,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val isSynced: Boolean = false
)

/**
 * Enum representing all supported expense categories.
 *
 * Each entry maps to a localized display string key for i18n support.
 * Categories can be looked up from their Chinese display name via [fromString].
 */
enum class ExpenseType(val displayNameKey: String) {
    /** Daily household goods and supplies */
    DAILY_GOODS("expense_type_daily_goods"),
    /** Luxury items and high-end purchases */
    LUXURY("expense_type_luxury"),
    /** Phone bills, internet, postage, and communication costs */
    COMMUNICATION("expense_type_communication"),
    /** General food and grocery items */
    FOOD("expense_type_food"),
    /** Snacks, candies, and confectionery */
    SNACKS("expense_type_snacks"),
    /** Cold beverages and ice cream */
    COLD_DRINKS("expense_type_cold_drinks"),
    /** Instant noodles, frozen meals, and other convenience foods */
    CONVENIENCE_FOOD("expense_type_convenience_food"),
    /** Textiles, fabrics, and related products */
    TEXTILES("expense_type_textiles"),
    /** Non-alcoholic beverages (tea, juice, etc.) */
    BEVERAGES("expense_type_beverages"),
    /** Cooking condiments, sauces, and seasonings */
    CONDIMENTS("expense_type_condiments"),
    /** Public transit, fuel, parking, and travel costs */
    TRANSPORTATION("expense_type_transportation"),
    /** Eating out at restaurants and food delivery */
    DINING("expense_type_dining"),
    /** Medical bills, prescriptions, and healthcare expenses */
    MEDICAL("expense_type_medical"),
    /** Fresh and dried fruits */
    FRUITS("expense_type_fruits"),
    /** Miscellaneous expenses that don't fit other categories */
    OTHER("expense_type_other"),
    /** Seafood, fish, and aquatic products */
    SEAFOOD("expense_type_seafood"),
    /** Dairy products (milk, cheese, yogurt, etc.) */
    DAIRY("expense_type_dairy"),
    /** Gifts, red envelopes, and social obligations */
    GIFTS("expense_type_gifts"),
    /** Vacation, hotel, and leisure travel costs */
    TRAVEL("expense_type_travel"),
    /** Government fees, taxes, and administrative charges */
    GOVERNMENT("expense_type_government"),
    /** Electricity, water, gas, and utility bills */
    UTILITIES("expense_type_utilities"),
    /** Haircuts, salon, and personal grooming services */
    BEAUTY("expense_type_beauty"),
    /** Tofu, soy milk, and bean-based food products */
    BEAN_PRODUCTS("expense_type_bean_products"),
    /** Skincare, makeup, and personal care items */
    COSMETICS("expense_type_cosmetics"),
    /** Phones, computers, gadgets, and electronic devices */
    ELECTRONICS("expense_type_electronics"),
    /** Refrigerators, washing machines, and home appliances */
    HOUSEHOLD_APPLIANCES("expense_type_household_appliances"),
    /** Tools, screws, paint, and hardware supplies */
    HARDWARE("expense_type_hardware"),
    /** Clothing, shoes, and apparel */
    CLOTHING("expense_type_clothing");

    companion object {
        /**
         * Converts a Chinese display name to the corresponding [ExpenseType].
         * Falls back to [OTHER] if no match is found.
         *
         * @param value The Chinese display name of the expense category.
         * @return The matching [ExpenseType], or [OTHER] if unrecognized.
         */
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
                "美容美发" -> BEAUTY
                "豆制品" -> BEAN_PRODUCTS
                "个护美妆" -> COSMETICS
                "电子产品" -> ELECTRONICS
                "家用电器" -> HOUSEHOLD_APPLIANCES
                "五金" -> HARDWARE
                "服装" -> CLOTHING
                else -> OTHER
            }
        }
    }
}
