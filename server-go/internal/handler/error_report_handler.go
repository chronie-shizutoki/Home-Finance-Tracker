package handler

import (
	"net/http"

	"homemoney/internal/models"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// ErrorReportHandler error report handler
type ErrorReportHandler struct {
	errorReportRepo *repository.ErrorReportRepository
}

// NewErrorReportHandler creates a new error report handler
func NewErrorReportHandler(errorReportRepo *repository.ErrorReportRepository) *ErrorReportHandler {
	return &ErrorReportHandler{errorReportRepo: errorReportRepo}
}

// ReportError submits an error report - corresponds to JS version POST /api/error/report
func (h *ErrorReportHandler) ReportError(c *gin.Context) {
	var request struct {
		ErrorType      string                 `json:"errorType" binding:"required"`
		Message        string                 `json:"message" binding:"required"`
		StackTrace     *string                `json:"stackTrace"`
		DeviceInfo     map[string]interface{} `json:"deviceInfo"`
		AppVersion     *string                `json:"appVersion"`
		AppBuild       *string                `json:"appBuild"`
		Environment    *string                `json:"environment"`
		MemberID       *string                `json:"memberId"`
		AdditionalInfo map[string]interface{} `json:"additionalInfo"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Error type and error message are required",
		})
		return
	}

	// Process device info - consistent with JS version: merge additionalInfo into deviceInfo
	var deviceInfo models.JSONMap
	if request.DeviceInfo != nil || request.AdditionalInfo != nil {
		deviceInfo = make(models.JSONMap)
		if request.DeviceInfo != nil {
			for k, v := range request.DeviceInfo {
				deviceInfo[k] = v
			}
		}
		if request.AdditionalInfo != nil {
			deviceInfo["additionalInfo"] = request.AdditionalInfo
		}
	}

	report := &models.ErrorReport{
		ErrorType:   request.ErrorType,
		Message:     request.Message,
		StackTrace:  request.StackTrace,
		DeviceInfo:  deviceInfo,
		AppVersion:  request.AppVersion,
		AppBuild:    request.AppBuild,
		Environment: request.Environment,
		MemberID:    request.MemberID,
		IsProcessed: false,
	}

	if err := h.errorReportRepo.Create(report); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Server error while processing error report",
		})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"success":  true,
		"message":  "Error report submitted successfully",
		"reportId": report.ID,
	})
}

// GetErrorReports retrieves the error report list - corresponds to JS version GET /api/errors
func (h *ErrorReportHandler) GetErrorReports(c *gin.Context) {
	var params repository.ErrorReportQueryParams
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Invalid query parameters",
		})
		return
	}

	if params.Page < 1 {
		params.Page = 1
	}
	if params.Limit < 1 || params.Limit > 100 {
		params.Limit = 20
	}

	reports, total, err := h.errorReportRepo.FindWithPagination(params)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Error retrieving error report list",
		})
		return
	}

	pages := int(total) / params.Limit
	if int(total)%params.Limit > 0 {
		pages++
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"reports": reports,
			"pagination": gin.H{
				"total": total,
				"page":  params.Page,
				"pages": pages,
				"limit": params.Limit,
			},
		},
	})
}

// ProcessErrorReport marks an error report as processed - corresponds to JS version PUT /api/errors/:reportId/process
func (h *ErrorReportHandler) ProcessErrorReport(c *gin.Context) {
	reportID := c.Param("reportId")
	if reportID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "Report ID cannot be empty",
		})
		return
	}

	report, err := h.errorReportRepo.FindByID(reportID)
	if err != nil || report == nil {
		c.JSON(http.StatusNotFound, gin.H{
			"success": false,
			"message": "Error report not found",
		})
		return
	}

	if err := h.errorReportRepo.MarkAsProcessed(reportID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Error updating error report status",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Error report has been marked as processed",
	})
}

// GetErrorStats retrieves error statistics - corresponds to JS version GET /api/errors/stats
func (h *ErrorReportHandler) GetErrorStats(c *gin.Context) {
	stats, err := h.errorReportRepo.GetErrorStats()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Error retrieving error statistics",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    stats,
	})
}