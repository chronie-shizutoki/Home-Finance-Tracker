package com.chronie.homemoney.ui.expense

import android.content.Context
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseType

/**
 * 支出类型本地化工具
 */
object ExpenseTypeLocalizer {
    
    fun getLocalizedName(context: Context, type: ExpenseType): String {
        val resourceId = when (type) {
            ExpenseType.DAILY_GOODS -> R.string.expense_type_daily_goods
            ExpenseType.LUXURY -> R.string.expense_type_luxury
            ExpenseType.COMMUNICATION -> R.string.expense_type_communication
            ExpenseType.FOOD -> R.string.expense_type_food
            ExpenseType.SNACKS -> R.string.expense_type_snacks
            ExpenseType.COLD_DRINKS -> R.string.expense_type_cold_drinks
            ExpenseType.CONVENIENCE_FOOD -> R.string.expense_type_convenience_food
            ExpenseType.TEXTILES -> R.string.expense_type_textiles
            ExpenseType.BEVERAGES -> R.string.expense_type_beverages
            ExpenseType.CONDIMENTS -> R.string.expense_type_condiments
            ExpenseType.TRANSPORTATION -> R.string.expense_type_transportation
            ExpenseType.DINING -> R.string.expense_type_dining
            ExpenseType.MEDICAL -> R.string.expense_type_medical
            ExpenseType.FRUITS -> R.string.expense_type_fruits
            ExpenseType.OTHER -> R.string.expense_type_other
            ExpenseType.SEAFOOD -> R.string.expense_type_seafood
            ExpenseType.DAIRY -> R.string.expense_type_dairy
            ExpenseType.GIFTS -> R.string.expense_type_gifts
            ExpenseType.TRAVEL -> R.string.expense_type_travel
            ExpenseType.GOVERNMENT -> R.string.expense_type_government
            ExpenseType.UTILITIES -> R.string.expense_type_utilities
        }
        return context.getString(resourceId)
    }
}
