package com.chronie.homemoney.core.hybrid

/**
 * 应用中的功能模块枚举
 */
enum class Feature(val displayNameKey: String) {
    EXPENSES("expense_management"),
    DEBTS("debt_management"),
    DONATIONS("donation_management"),
    CHARTS("charts_and_statistics"),
    MEMBERSHIP("membership_management"),
    MINIAPPS("miniapp_management");

    companion object {
        fun fromRoute(route: String): Feature? {
            return when (route) {
                "expenses" -> EXPENSES
                "debts" -> DEBTS
                "donations" -> DONATIONS
                "charts" -> CHARTS
                "membership" -> MEMBERSHIP
                "miniapps" -> MINIAPPS
                else -> null
            }
        }
    }

    fun toRoute(): String {
        return when (this) {
            EXPENSES -> "expenses"
            DEBTS -> "debts"
            DONATIONS -> "donations"
            CHARTS -> "charts"
            MEMBERSHIP -> "membership"
            MINIAPPS -> "miniapps"
        }
    }
}
