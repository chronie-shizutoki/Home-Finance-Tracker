package com.chronie.homemoney.data.remote.api

import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.data.remote.dto.ExpenseListResponse
import com.chronie.homemoney.data.remote.dto.ExpenseStatisticsDto
import com.chronie.homemoney.data.remote.dto.SyncRequestDto
import com.chronie.homemoney.data.remote.dto.SyncResponseDto
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for expense-related server endpoints.
 *
 * Provides CRUD operations for expense records, batch creation,
 * statistical queries, and offline-first sync functionality.
 * All endpoints target the /api/expenses base path.
 */
interface ExpenseApi {

    /**
     * Retrieves a paginated list of expenses with optional filters.
     *
     * @param page Page number (1-based).
     * @param limit Number of records per page.
     * @param keyword Full-text search term for remark field.
     * @param type Filter by expense type (e.g. "food", "transport").
     * @param month Filter by month (YYYY-MM format).
     * @param minAmount Minimum expense amount.
     * @param maxAmount Maximum expense amount.
     * @param sort Sort order (e.g. "dateDesc", "dateAsc").
     * @return Paginated list of expenses matching the criteria.
     */
    @GET("api/expenses")
    suspend fun getExpenses(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("type") type: String? = null,
        @Query("month") month: String? = null,
        @Query("minAmount") minAmount: Double? = null,
        @Query("maxAmount") maxAmount: Double? = null,
        @Query("sort") sort: String = "dateDesc"
    ): Response<ExpenseListResponse>

    /**
     * Adds a single expense record.
     *
     * @param expense The expense data to create.
     * @return The created expense DTO.
     */
    @POST("api/expenses")
    suspend fun addExpense(
        @Body expense: ExpenseDto
    ): Response<ExpenseDto>

    /**
     * Creates an expense and returns a wrapped API response with metadata.
     *
     * @param expense The expense data to create.
     * @return An [ApiResponse] wrapping the created expense.
     */
    @POST("api/expenses")
    suspend fun createExpense(
        @Body expense: ExpenseDto
    ): Response<com.chronie.homemoney.data.remote.dto.ApiResponse<ExpenseDto>>

    /**
     * Updates an existing expense record by its ID.
     *
     * @param id The unique identifier of the expense to update.
     * @param expense The updated expense data.
     * @return An [ApiResponse] wrapping the updated expense.
     */
    @PUT("api/expenses/{id}")
    suspend fun updateExpense(
        @Path("id") id: String,
        @Body expense: ExpenseDto
    ): Response<com.chronie.homemoney.data.remote.dto.ApiResponse<ExpenseDto>>

    /**
     * Creates multiple expense records in a single request.
     *
     * @param expenses List of expense DTOs to create.
     * @return List of created expense DTOs.
     */
    @POST("api/expenses/batch")
    suspend fun addExpensesBatch(
        @Body expenses: List<ExpenseDto>
    ): Response<List<ExpenseDto>>

    /**
     * Soft-deletes an expense by setting its deleted_at timestamp.
     *
     * @param id The unique identifier of the expense to soft-delete.
     * @return Empty response body; check HTTP status for success.
     */
    @DELETE("api/expenses/{id}")
    suspend fun deleteExpense(
        @Path("id") id: String
    ): Response<Unit>

    /**
     * Permanently deletes an expense record without soft-delete.
     *
     * @param id The unique identifier of the expense to hard-delete.
     * @return Empty response body; check HTTP status for success.
     */
    @DELETE("api/expenses/{id}/hard")
    suspend fun hardDeleteExpense(
        @Path("id") id: String
    ): Response<Unit>

    /**
     * Retrieves aggregated expense statistics with optional filters.
     *
     * @param keyword Full-text search term.
     * @param type Expense type filter.
     * @param month Month filter (YYYY-MM format).
     * @param minAmount Minimum amount filter.
     * @param maxAmount Maximum amount filter.
     * @return Aggregated statistics (total, counts by type, etc.).
     */
    @GET("api/expenses/statistics")
    suspend fun getStatistics(
        @Query("keyword") keyword: String? = null,
        @Query("type") type: String? = null,
        @Query("month") month: String? = null,
        @Query("minAmount") minAmount: Double? = null,
        @Query("maxAmount") maxAmount: Double? = null
    ): Response<ExpenseStatisticsDto>

    /**
     * Performs bidirectional sync of expense data.
     *
     * Sends locally modified/deleted records and receives server-side changes
     * in a single round-trip, supporting offline-first operation.
     *
     * @param request The sync payload containing local changes.
     * @return Server-side changes applied since the last sync.
     */
    @POST("api/expenses/sync")
    suspend fun syncExpenses(
        @Body request: SyncRequestDto
    ): Response<SyncResponseDto>
}
