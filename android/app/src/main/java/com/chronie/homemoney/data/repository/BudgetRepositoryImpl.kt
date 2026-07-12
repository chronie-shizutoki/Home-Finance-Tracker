package com.chronie.homemoney.data.repository

import com.chronie.homemoney.data.local.dao.BudgetDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.BudgetEntity
import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.domain.model.BudgetUsage
import com.chronie.homemoney.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budget Repository Implementation
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao
) : BudgetRepository {
    
    override fun getBudget(): Flow<Budget?> {
        return budgetDao.getBudget().map { entity ->
            entity?.let {
                Budget(
                    monthlyLimit = it.monthlyLimit,
                    warningThreshold = it.warningThreshold,
                    isEnabled = it.isEnabled
                )
            }
        }
    }
    
    override suspend fun getBudgetOnce(): Budget? {
        return budgetDao.getBudgetOnce()?.let {
            Budget(
                monthlyLimit = it.monthlyLimit,
                warningThreshold = it.warningThreshold,
                isEnabled = it.isEnabled
            )
        }
    }
    
    override suspend fun saveBudget(budget: Budget) {
        val entity = BudgetEntity(
            id = 1,
            monthlyLimit = budget.monthlyLimit,
            warningThreshold = budget.warningThreshold,
            isEnabled = budget.isEnabled,
            updatedAt = System.currentTimeMillis()
        )
        budgetDao.insertBudget(entity)
    }
    
    override suspend fun getCurrentMonthUsage(): BudgetUsage? {
        return try {
            android.util.Log.d("BudgetRepository", "Getting current month usage...")
            
            val budget = getBudgetOnce()
            if (budget == null) {
                android.util.Log.d("BudgetRepository", "No budget found")
                return null
            }
            
            if (!budget.isEnabled) {
                android.util.Log.d("BudgetRepository", "Budget is not enabled")
                return null
            }
            
            // Get current month start and end date strings
            val now = java.time.LocalDate.now()
            val yearMonth = java.time.YearMonth.from(now)
            val startOfMonth = yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val endOfMonth = yearMonth.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            
            android.util.Log.d("BudgetRepository", "Querying expenses from $startOfMonth to $endOfMonth")
            
            // Query total month spending amount
            val currentSpending: Double = expenseDao.getTotalAmountByDateRange(startOfMonth, endOfMonth) ?: 0.0
            
            android.util.Log.d("BudgetRepository", "Current spending: $currentSpending")
        val remainingAmount: Double = budget.monthlyLimit - currentSpending
        val spendingPercentage = if (budget.monthlyLimit > 0) {
            (currentSpending / budget.monthlyLimit) * 100
        } else {
            0.0
        }
        
        val isOverLimit = currentSpending > budget.monthlyLimit
        val isNearLimit = spendingPercentage >= (budget.warningThreshold * 100)
        
        // Calculate daily average spending amount
        val currentDay = now.dayOfMonth
        val dailyAverage = if (currentDay > 0) currentSpending / currentDay else 0.0
        
        // Calculate recommended daily spending amount
        val daysInMonth = yearMonth.lengthOfMonth()
        val remainingDays = daysInMonth - currentDay
        val recommendedDaily = if (remainingDays > 0) remainingAmount / remainingDays else 0.0
        
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
            val currentMonth = now.format(formatter)
            
            val usage = BudgetUsage(
                monthlyLimit = budget.monthlyLimit,
                currentSpending = currentSpending,
                remainingAmount = remainingAmount,
                spendingPercentage = spendingPercentage,
                warningThreshold = budget.warningThreshold,
                isOverLimit = isOverLimit,
                isNearLimit = isNearLimit,
                dailyAverage = dailyAverage,
                recommendedDaily = recommendedDaily,
                currentMonth = currentMonth
            )
            
            android.util.Log.d("BudgetRepository", "Budget usage calculated successfully: $usage")
            usage
        } catch (e: Exception) {
            android.util.Log.e("BudgetRepository", "Error calculating budget usage", e)
            null
        }
    }
    
    override suspend fun toggleBudgetEnabled(enabled: Boolean) {
        val current = budgetDao.getBudgetOnce()
        if (current != null) {
            budgetDao.updateBudget(current.copy(isEnabled = enabled))
        } else {
            // If no budget is set, create a default one with enabled status
            val defaultBudget = BudgetEntity(
                id = 1,
                monthlyLimit = 0.0,
                warningThreshold = 0.8,
                isEnabled = enabled,
                updatedAt = System.currentTimeMillis()
            )
            budgetDao.insertBudget(defaultBudget)
        }
    }
}
