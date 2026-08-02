package com.chronie.homemoney.di

import com.chronie.homemoney.data.local.dao.BudgetDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.data.remote.api.MemberApi
import com.chronie.homemoney.data.repository.BudgetRepositoryImpl
import com.chronie.homemoney.data.repository.ExpenseRepositoryImpl
import com.chronie.homemoney.data.repository.MemberRepositoryImpl
import com.chronie.homemoney.domain.repository.BudgetRepository
import com.chronie.homemoney.domain.repository.ExpenseRepository
import com.chronie.homemoney.domain.repository.MemberRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module that binds repository interfaces to their concrete implementations.
 *
 * Provides singleton bindings:
 * - [ExpenseRepository] → [ExpenseRepositoryImpl] (backed by Room + Retrofit)
 * - [BudgetRepository] → [BudgetRepositoryImpl] (backed by Room)
 * - [MemberRepository] → [MemberRepositoryImpl] (backed by Retrofit)
 *
 * This module is the single point of dependency wiring for the domain layer.
 * Use cases receive properly injected repositories without knowing about
 * the underlying data sources (Room, Retrofit, etc.).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideExpenseRepository(
        expenseDao: ExpenseDao,
        expenseApi: ExpenseApi
    ): ExpenseRepository {
        return ExpenseRepositoryImpl(expenseDao, expenseApi)
    }
    
    @Provides
    @Singleton
    fun provideBudgetRepository(
        budgetDao: BudgetDao,
        expenseDao: ExpenseDao
    ): BudgetRepository {
        return BudgetRepositoryImpl(budgetDao, expenseDao)
    }
    
    @Provides
    @Singleton
    fun provideMemberRepository(
        memberApi: MemberApi
    ): MemberRepository {
        return MemberRepositoryImpl(memberApi)
    }
}
