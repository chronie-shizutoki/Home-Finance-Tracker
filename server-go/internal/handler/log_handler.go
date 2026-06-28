package handler

import (
	"net/http"
	"strconv"

	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// LogHandler log handler
type LogHandler struct {
	logService *service.LogService
}

// NewLogHandler creates a log handler instance
func NewLogHandler(logService *service.LogService) *LogHandler {
	return &LogHandler{logService: logService}
}

// ReceiveLog receives logs sent from frontend
// @Summary Receive operation logs
// @Description Receive and asynchronously save operation logs sent from frontend
// @Tags logs
// @Accept json
// @Produce json
// @Param log body service.LogData true "Log data"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs [post]
func (h *LogHandler) ReceiveLog(c *gin.Context) {
	var logData service.LogData

	// Bind request body
	if err := c.ShouldBindJSON(&logData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Request body cannot be empty",
			"errors":  nil,
		})
		return
	}

	// Validate required fields
	if logData.Timestamp == "" || logData.Type == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Invalid parameters",
			"errors":  nil,
		})
		return
	}

	// Handle logs asynchronously, don't block response
	h.logService.HandleLog(c, logData)

	// Return success response immediately - consistent with JS apiResponse.success format
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Operation successful",
		"data": gin.H{
			"message": "Log received successfully",
		},
	})
}

// GetLogsList gets log list
// @Summary Get log list
// @Description Get log list, supports pagination and filtering
// @Tags logs
// @Produce json
// @Param limit query int false "Limit per page" default(100)
// @Param offset query int false "Offset" default(0)
// @Param type query string false "Log type"
// @Param startDate query string false "Start date"
// @Param endDate query string false "End date"
// @Param username query string false "Username"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs [get]
func (h *LogHandler) GetLogsList(c *gin.Context) {
	var params service.QueryLogParams

	// Bind query parameters
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Invalid query parameters",
			"errors":  nil,
		})
		return
	}

	// Set default values
	if params.Limit <= 0 || params.Limit > 1000 {
		params.Limit = 100
	}
	if params.Offset < 0 {
		params.Offset = 0
	}

	// Query logs
	logs, total, err := h.logService.GetLogs(params)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Internal server error",
			"errors":  nil,
		})
		return
	}

	// Calculate pagination info
	page := params.Offset/params.Limit + 1
	totalPages := (total + params.Limit - 1) / params.Limit

	// Return result - consistent with JS apiResponse.success format
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Log list retrieved successfully",
		"data": gin.H{
			"logs":       logs,
			"total":      total,
			"page":       page,
			"pageSize":   params.Limit,
			"totalPages": totalPages,
		},
	})
}

// GetLogStats gets log statistics
// @Summary Get log statistics
// @Description Get log statistics, including counts of different log types
// @Tags logs
// @Produce json
// @Param startDate query string false "Start date"
// @Param endDate query string false "End date"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs/stats [get]
func (h *LogHandler) GetLogStats(c *gin.Context) {
	var params service.QueryLogParams

	// Bind query parameters
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Invalid query parameters",
			"errors":  nil,
		})
		return
	}

	// Get statistics
	stats, err := h.logService.GetLogStats(params)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to get log statistics",
			"errors":  err,
		})
		return
	}

	// Return result - consistent with JS apiResponse.success format
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Log statistics retrieved successfully",
		"data": gin.H{
			"total":     stats.Total,
			"typeStats": stats.TypeStats,
			"period":    stats.Period,
		},
	})
}

// CleanLogs cleans expired logs
// @Summary Clean expired logs
// @Description Clean logs older than the specified number of days
// @Tags logs
// @Produce json
// @Param days query int false "Days to keep" default(45)
// @Success 200 {object} map[string]interface{}
// @Router /api/logs/clean [delete]
func (h *LogHandler) CleanLogs(c *gin.Context) {
	// Get days parameter
	daysStr := c.DefaultQuery("days", "45")
	days, err := strconv.Atoi(daysStr)
	if err != nil || days < 1 {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Invalid log cleanup parameters",
			"errors":  nil,
		})
		return
	}

	// Clean logs
	deletedCount, err := h.logService.CleanLogs(days)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to clean logs",
			"errors":  err,
		})
		return
	}

	// Return result - consistent with JS apiResponse.success format
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": gin.H{
			"message":      "Successfully cleaned " + strconv.FormatInt(deletedCount, 10) + " expired logs",
			"deletedCount": deletedCount,
			"keptDays":     days,
		},
		"data": "Logs cleaned successfully",
	})
}