package com.chronie.homemoney.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支出记录 Repository 实现
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val expenseApi: ExpenseApi
) : ExpenseRepository {
    
    override fun getExpenses(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Flow<PagingData<Expense>> {
        return Pager<Int, Expense>(
            config = PagingConfig(
                pageSize = limit,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                // TODO: 实现 PagingSource
                throw NotImplementedError("PagingSource not implemented yet")
            }
        ).flow
    }
    
    override suspend fun getExpensesList(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Result<List<Expense>> {
        return try {
            // 首先尝试从本地数据库获取
            // 注意：这里简化实现，直接获取所有数据然后在内存中分页
            // 后续可以优化为使用 Room 的分页查询
            val allExpenses = expenseDao.getAllExpenses().first()
            
            // 应用分页
            val startIndex = (page - 1) * limit
            val endIndex = minOf(startIndex + limit, allExpenses.size)
            val localExpenses = if (startIndex < allExpenses.size) {
                allExpenses.subList(startIndex, endIndex).map { ExpenseMapper.toDomain(it) }
            } else {
                emptyList()
            }
            
            // 如果本地有数据，返回本地数据
            if (localExpenses.isNotEmpty()) {
                Result.success(localExpenses)
            } else {
                // 如果本地没有数据，从服务器获取
                val response = expenseApi.getExpenses(
                    page = page,
                    limit = limit,
                    keyword = filters.keyword,
                    type = filters.type?.let { getChineseTypeName(it) },
                    month = filters.month,
                    minAmount = filters.minAmount,
                    maxAmount = filters.maxAmount,
                    sort = getSortString(filters.sortBy)
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    val expenses = apiResponse.data.map { ExpenseMapper.toDomain(it) }
                    
                    // 保存到本地数据库
                    expenses.forEach { expense ->
                        expenseDao.insertExpense(ExpenseMapper.toEntity(expense))
                    }
                    
                    Result.success(expenses)
                } else {
                    Result.failure(Exception("Failed to fetch expenses: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getExpenseById(id: String): Result<Expense> {
        return try {
            val entity = expenseDao.getExpenseById(id)
            if (entity != null) {
                Result.success(ExpenseMapper.toDomain(entity))
            } else {
                Result.failure(Exception("Expense not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun addExpense(expense: Expense): Result<Expense> {
        return try {
            // 保存到本地数据库
            val entity = ExpenseMapper.toEntity(expense)
            expenseDao.insertExpense(entity)
            
            // TODO: 添加到同步队列
            
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateExpense(expense: Expense): Result<Expense> {
        return try {
            val entity = ExpenseMapper.toEntity(expense)
            expenseDao.updateExpense(entity)
            
            // TODO: 添加到同步队列
            
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteExpense(id: String): Result<Unit> {
        return try {
            expenseDao.deleteExpenseById(id)
            
            // TODO: 添加到同步队列
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getStatistics(filters: ExpenseFilters): Result<ExpenseStatistics> {
        return try {
            // 从本地数据库计算统计数据
            val allExpenses = expenseDao.getAllExpenses().first()
            val expenses = allExpenses.map { ExpenseMapper.toDomain(it) }
            
            if (expenses.isEmpty()) {
                return Result.success(
                    ExpenseStatistics(
                        count = 0,
                        totalAmount = 0.0,
                        averageAmount = 0.0,
                        medianAmount = 0.0,
                        minAmount = 0.0,
                        maxAmount = 0.0
                    )
                )
            }
            
            val amounts = expenses.map { it.amount }.sorted()
            val total = amounts.sum()
            val average = total / amounts.size
            val median = if (amounts.size % 2 == 0) {
                (amounts[amounts.size / 2 - 1] + amounts[amounts.size / 2]) / 2
            } else {
                amounts[amounts.size / 2]
            }
            
            Result.success(
                ExpenseStatistics(
                    count = expenses.size,
                    totalAmount = total,
                    averageAmount = average,
                    medianAmount = median,
                    minAmount = amounts.first(),
                    maxAmount = amounts.last()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun syncWithServer(): Result<Unit> {
        // TODO: 实现同步逻辑
        return Result.success(Unit)
    }
    
    private fun getChineseTypeName(type: com.chronie.homemoney.domain.model.ExpenseType): String {
        return when (type) {
            com.chronie.homemoney.domain.model.ExpenseType.DAILY_GOODS -> "日常用品"
            com.chronie.homemoney.domain.model.ExpenseType.LUXURY -> "奢侈品"
            com.chronie.homemoney.domain.model.ExpenseType.COMMUNICATION -> "通讯费用"
            com.chronie.homemoney.domain.model.ExpenseType.FOOD -> "食品"
            com.chronie.homemoney.domain.model.ExpenseType.SNACKS -> "零食糖果"
            com.chronie.homemoney.domain.model.ExpenseType.COLD_DRINKS -> "冷饮"
            com.chronie.homemoney.domain.model.ExpenseType.CONVENIENCE_FOOD -> "方便食品"
            com.chronie.homemoney.domain.model.ExpenseType.TEXTILES -> "纺织品"
            com.chronie.homemoney.domain.model.ExpenseType.BEVERAGES -> "饮品"
            com.chronie.homemoney.domain.model.ExpenseType.CONDIMENTS -> "调味品"
            com.chronie.homemoney.domain.model.ExpenseType.TRANSPORTATION -> "交通出行"
            com.chronie.homemoney.domain.model.ExpenseType.DINING -> "餐饮"
            com.chronie.homemoney.domain.model.ExpenseType.MEDICAL -> "医疗费用"
            com.chronie.homemoney.domain.model.ExpenseType.FRUITS -> "水果"
            com.chronie.homemoney.domain.model.ExpenseType.OTHER -> "其他"
            com.chronie.homemoney.domain.model.ExpenseType.SEAFOOD -> "水产品"
            com.chronie.homemoney.domain.model.ExpenseType.DAIRY -> "乳制品"
            com.chronie.homemoney.domain.model.ExpenseType.GIFTS -> "礼物人情"
            com.chronie.homemoney.domain.model.ExpenseType.TRAVEL -> "旅行度假"
            com.chronie.homemoney.domain.model.ExpenseType.GOVERNMENT -> "政务"
            com.chronie.homemoney.domain.model.ExpenseType.UTILITIES -> "水电煤气"
        }
    }
    
    private fun getSortString(sortOption: SortOption): String {
        return when (sortOption) {
            SortOption.DATE_ASC -> "dateAsc"
            SortOption.DATE_DESC -> "dateDesc"
            SortOption.AMOUNT_ASC -> "amountAsc"
            SortOption.AMOUNT_DESC -> "amountDesc"
        }
    }
}
