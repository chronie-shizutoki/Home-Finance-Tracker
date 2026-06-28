package handlers

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"homemoney/internal/models"
	"homemoney/internal/repository"
	"homemoney/pkg/utils"

	"github.com/gin-gonic/gin"
)

// ExpenseHandler expense record handler
type ExpenseHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewExpenseHandler creates a new expense handler
func NewExpenseHandler(expenseRepo *repository.ExpenseRepository) *ExpenseHandler {
	return &ExpenseHandler{
		expenseRepo: expenseRepo,
	}
}

// GetExpenses retrieves the expense record list
func (h *ExpenseHandler) GetExpenses(c *gin.Context) {
	// Parse query parameters
	query, err := h.parseExpenseQuery(c)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}

	// Execute query
	expenses, total, err := h.expenseRepo.FindWithPagination(query)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}

	// Get metadata
	meta, err := h.expenseRepo.GetMeta()
	if err != nil {
		// Metadata fetch failure doesn't affect main functionality
		meta = &models.ExpenseMeta{}
	}

	// Return format fully consistent with Node.js
	page := query.Offset/query.Limit + 1
	c.JSON(http.StatusOK, gin.H{
		"data":  expenses,
		"total": total,
		"page":  page,
		"limit": query.Limit,
		"meta":  meta,
	})
}

// CreateExpense creates an expense record - fully consistent with JS version addExpense
func (h *ExpenseHandler) CreateExpense(c *gin.Context) {
	var request struct {
		ID        string  `json:"id"`
		Type      string  `json:"type"`
		Remark    *string `json:"remark"`
		Amount    float64 `json:"amount"`
		Time      *string `json:"time"`
		Date      *string `json:"date"`
		Version   *int    `json:"version"`
		UpdatedAt *int64  `json:"updatedAt"`
	}
	if err := c.ShouldBindJSON(&request); err != nil {
		utils.ErrorResponseWithStatus(c, "Expense type and amount are required", err.Error(), http.StatusBadRequest)
		return
	}

	// Backend data validation
	if request.Type == "" || request.Amount <= 0 {
		utils.ErrorResponseWithStatus(c, "Expense type and amount are required", "", http.StatusBadRequest)
		return
	}

	// Date handling - fully consistent with JS version: date > time > default to today
	dateStr := time.Now().Format("2006-01-02")
	if request.Date != nil && *request.Date != "" {
		dateStr = parseDateString(*request.Date)
	} else if request.Time != nil && *request.Time != "" {
		dateStr = parseDateString(*request.Time)
	}

	expense := models.Expense{
		ID:     request.ID,
		Type:   request.Type,
		Remark: request.Remark,
		Amount: request.Amount,
		Date:   dateStr,
	}
	if request.Version != nil {
		expense.Version = *request.Version
	}
	if request.UpdatedAt != nil {
		expense.UpdatedAt = *request.UpdatedAt
	}

	// Save record
	if err := h.expenseRepo.Create(&expense); err != nil {
		utils.ErrorResponseWithStatus(c, "Unable to add record", err.Error(), http.StatusInternalServerError)
		return
	}

	// Return format fully consistent with Node.js - directly return created object
	c.JSON(http.StatusCreated, expense)
}

// parseDateString parses date string, extracts YYYY-MM-DD format - consistent with JS dayjs().format('YYYY-MM-DD')
func parseDateString(s string) string {
	// Try multiple date format parsing
	formats := []string{
		"2006-01-02",
		"2006-01-02T15:04:05Z",
		"2006-01-02T15:04:05.000Z",
		time.RFC3339,
	}
	for _, format := range formats {
		if t, err := time.Parse(format, s); err == nil {
			return t.Format("2006-01-02")
		}
	}
	// If all parsing fails, extract first 10 characters as date
	if len(s) >= 10 {
		return s[:10]
	}
	return time.Now().Format("2006-01-02")
}

// GetExpenseStatistics retrieves expense statistics
func (h *ExpenseHandler) GetExpenseStatistics(c *gin.Context) {
	query, err := h.parseExpenseQuery(c)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to get statistics", err.Error(), http.StatusInternalServerError)
		return
	}

	stats, err := h.expenseRepo.GetStatistics(query)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to get statistics", err.Error(), http.StatusInternalServerError)
		return
	}

	// Return format fully consistent with Node.js
	c.JSON(http.StatusOK, stats)
}

// DeleteExpense deletes an expense record
func (h *ExpenseHandler) DeleteExpense(c *gin.Context) {
	id := c.Param("id")

	// Check if record exists
	exists, err := h.expenseRepo.Exists(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}
	if !exists {
		utils.ErrorResponseWithStatus(c, "Record not found", "", http.StatusNotFound)
		return
	}

	// Delete record
	if err := h.expenseRepo.Delete(id); err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}

	// Return format fully consistent with Node.js - only return status code
	c.Status(http.StatusNoContent)
}

// UpdateExpense updates an expense record - conflict detection consistent with JS version
func (h *ExpenseHandler) UpdateExpense(c *gin.Context) {
	id := c.Param("id")

	// Find existing record
	expense, err := h.expenseRepo.FindByID(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to find record", err.Error(), http.StatusInternalServerError)
		return
	}
	if expense == nil {
		utils.ErrorResponseWithStatus(c, "Record to update not found", "", http.StatusNotFound)
		return
	}

	// Parse request data
	var updateData struct {
		Type      *string  `json:"type"`
		Remark    *string  `json:"remark"`
		Amount    *float64 `json:"amount"`
		Date      *string  `json:"date"`
		Time      *string  `json:"time"`
		Version   *int     `json:"version"`
		UpdatedAt *int64   `json:"updatedAt"`
	}
	if err := c.ShouldBindJSON(&updateData); err != nil {
		utils.ErrorResponseWithStatus(c, "Invalid request parameters", err.Error(), http.StatusBadRequest)
		return
	}

	// Check if at least one field needs updating
	if updateData.Type == nil && updateData.Amount == nil && updateData.Remark == nil && updateData.Time == nil && updateData.Date == nil {
		utils.ErrorResponseWithStatus(c, "At least one field must be provided for update", "", http.StatusBadRequest)
		return
	}

	// Amount validation
	if updateData.Amount != nil && (*updateData.Amount <= 0) {
		utils.ErrorResponseWithStatus(c, "Amount must be a valid positive number", "", http.StatusBadRequest)
		return
	}

	// Conflict detection - consistent with JS version
	clientVersion := expense.Version + 1
	if updateData.Version != nil {
		clientVersion = *updateData.Version
	}
	clientUpdatedAt := time.Now().UnixMilli()
	if updateData.UpdatedAt != nil {
		clientUpdatedAt = *updateData.UpdatedAt
	}

	if clientUpdatedAt <= expense.UpdatedAt {
		c.JSON(http.StatusConflict, gin.H{
			"error":             "Conflict: server has newer version",
			"serverVersion":     expense.Version,
			"serverUpdatedAt":   expense.UpdatedAt,
			"currentData":       expense,
		})
		return
	}

	// Update fields
	if updateData.Type != nil {
		expense.Type = *updateData.Type
	}
	if updateData.Remark != nil {
		expense.Remark = updateData.Remark
	}
	if updateData.Amount != nil {
		expense.Amount = *updateData.Amount
	}
	if updateData.Date != nil {
		expense.Date = *updateData.Date
	} else if updateData.Time != nil {
		expense.Date = (*updateData.Time)[:10]
	}
	expense.Version = clientVersion
	expense.UpdatedAt = clientUpdatedAt

	// Save update
	if err := h.expenseRepo.Update(expense); err != nil {
		utils.ErrorResponseWithStatus(c, "Unable to update record", err.Error(), http.StatusInternalServerError)
		return
	}

	c.JSON(http.StatusOK, expense)
}

// HardDeleteExpense hard deletes an expense record
func (h *ExpenseHandler) HardDeleteExpense(c *gin.Context) {
	id := c.Param("id")

	if err := h.expenseRepo.HardDelete(id); err != nil {
		utils.ErrorResponseWithStatus(c, "Unable to delete record", err.Error(), http.StatusInternalServerError)
		return
	}

	c.Status(http.StatusNoContent)
}

// GetExpensesByDate groups expenses by date - fully consistent with JS version
func (h *ExpenseHandler) GetExpensesByDate(c *gin.Context) {
	query, err := h.parseExpenseQuery(c)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}

	grouped, total, meta, err := h.expenseRepo.GetExpensesByDate(query)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to read data", err.Error(), http.StatusInternalServerError)
		return
	}

	page := query.Offset/query.Limit + 1
	c.JSON(http.StatusOK, gin.H{
		"data":  grouped,
		"total": total,
		"page":  page,
		"limit": query.Limit,
		"meta":  meta,
	})
}

// SyncExpenses syncs expense records - fully consistent with JS version
func (h *ExpenseHandler) SyncExpenses(c *gin.Context) {
	var syncRequest struct {
		LastSyncTime *int64            `json:"lastSyncTime"`
		Changes      []models.Expense  `json:"changes"`
		LocalIDs     []string          `json:"localIds"`
	}
	if err := c.ShouldBindJSON(&syncRequest); err != nil {
		// Accept empty request body, return empty result
		c.JSON(http.StatusOK, gin.H{
			"serverChanges": []models.Expense{},
			"conflicts":     []gin.H{},
			"syncTime":      time.Now().UnixMilli(),
		})
		return
	}

	serverChanges, conflicts, err := h.expenseRepo.SyncExpenses(syncRequest.LastSyncTime, syncRequest.Changes, syncRequest.LocalIDs)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Sync failed", err.Error(), http.StatusInternalServerError)
		return
	}

	// Ensure empty arrays are returned instead of null
	if serverChanges == nil {
		serverChanges = []models.Expense{}
	}
	if conflicts == nil {
		conflicts = []gin.H{}
	}

	c.JSON(http.StatusOK, gin.H{
		"serverChanges": serverChanges,
		"conflicts":     conflicts,
		"syncTime":      time.Now().UnixMilli(),
	})
}

// parseExpenseQuery parses expense query parameters
func (h *ExpenseHandler) parseExpenseQuery(c *gin.Context) (*models.ExpenseQuery, error) {
	query := &models.ExpenseQuery{}

	// Parse basic parameters
	query.Keyword = c.Query("keyword")
	query.Type = c.Query("type")
	query.Month = c.Query("month")

	// Parse pagination parameters
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))
	query.Limit = limit
	query.Offset = (page - 1) * limit

	// Parse sort parameters
	query.Sort = c.DefaultQuery("sort", "dateDesc")

	// Parse amount parameters
	if minAmountStr := c.Query("minAmount"); minAmountStr != "" {
		if minAmount, err := strconv.ParseFloat(minAmountStr, 64); err == nil {
			query.MinAmount = &minAmount
		}
	}
	if maxAmountStr := c.Query("maxAmount"); maxAmountStr != "" {
		if maxAmount, err := strconv.ParseFloat(maxAmountStr, 64); err == nil {
			query.MaxAmount = &maxAmount
		}
	}

	// Parse date parameters (use strings directly)
	query.StartDate = c.Query("startDate")
	query.EndDate = c.Query("endDate")

	// If month is provided, convert it to a date range
	if query.Month != "" && (query.StartDate == "" || query.EndDate == "") {
		startDate, endDate, err := query.ToMonthRange()
		if err != nil {
			return nil, fmt.Errorf("failed to parse month: %w", err)
		}
		query.StartDate = startDate
		query.EndDate = endDate
	}

	// Validate query parameters
	if err := query.Validate(); err != nil {
		return nil, fmt.Errorf("query parameter validation failed: %w", err)
	}

	return query, nil
}

// TestHandler test endpoint, used to verify API behavior
func (h *ExpenseHandler) TestHandler(c *gin.Context) {
	// Simple health check endpoint
	c.JSON(http.StatusOK, gin.H{
		"message": "Expense API is working",
		"time":    time.Now(),
	})
}

// BatchCreateExpense batch creates expense records
func (h *ExpenseHandler) BatchCreateExpense(c *gin.Context) {
	var expenses []models.Expense
	if err := c.ShouldBindJSON(&expenses); err != nil {
		utils.ErrorResponseWithStatus(c, "Invalid request parameters", err.Error(), http.StatusBadRequest)
		return
	}

	if len(expenses) == 0 {
		utils.ErrorResponseWithStatus(c, "Expense record list cannot be empty", "", http.StatusBadRequest)
		return
	}

	// Batch create
	if err := h.expenseRepo.BatchCreate(expenses); err != nil {
		utils.ErrorResponseWithStatus(c, "Batch creation failed", err.Error(), http.StatusInternalServerError)
		return
	}

	c.JSON(http.StatusCreated, utils.SuccessResponse(gin.H{
		"message": fmt.Sprintf("Successfully created %d expense records", len(expenses)),
		"count":   len(expenses),
	}))
}

// GetExpenseByID gets expense record by ID
func (h *ExpenseHandler) GetExpenseByID(c *gin.Context) {
	id := c.Param("id")

	expense, err := h.expenseRepo.FindByID(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "Failed to find record", err.Error(), http.StatusInternalServerError)
		return
	}
	if expense == nil {
		utils.ErrorResponseWithStatus(c, "Record not found", "", http.StatusNotFound)
		return
	}

	c.JSON(http.StatusOK, utils.SuccessResponse(expense))
}
