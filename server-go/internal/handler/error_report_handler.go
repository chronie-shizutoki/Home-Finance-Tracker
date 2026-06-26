package handler

import (
	"net/http"

	"homemoney/internal/models"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// ErrorReportHandler 错误报告处理器
type ErrorReportHandler struct {
	errorReportRepo *repository.ErrorReportRepository
}

// NewErrorReportHandler 创建新的错误报告处理器
func NewErrorReportHandler(errorReportRepo *repository.ErrorReportRepository) *ErrorReportHandler {
	return &ErrorReportHandler{errorReportRepo: errorReportRepo}
}

// ReportError 提交错误报告 - 对应JS版本的 POST /api/error/report
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
			"message": "错误类型和错误消息是必填项",
		})
		return
	}

	// 处理设备信息 - 与JS版本一致：合并additionalInfo到deviceInfo
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
			"message": "服务器处理错误报告时出现问题",
		})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"success":  true,
		"message":  "错误报告已成功提交",
		"reportId": report.ID,
	})
}

// GetErrorReports 获取错误报告列表 - 对应JS版本的 GET /api/errors
func (h *ErrorReportHandler) GetErrorReports(c *gin.Context) {
	var params repository.ErrorReportQueryParams
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "查询参数错误",
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
			"message": "获取错误报告列表时发生错误",
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

// ProcessErrorReport 标记错误报告为已处理 - 对应JS版本的 PUT /api/errors/:reportId/process
func (h *ErrorReportHandler) ProcessErrorReport(c *gin.Context) {
	reportID := c.Param("reportId")
	if reportID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "报告ID不能为空",
		})
		return
	}

	report, err := h.errorReportRepo.FindByID(reportID)
	if err != nil || report == nil {
		c.JSON(http.StatusNotFound, gin.H{
			"success": false,
			"message": "未找到指定的错误报告",
		})
		return
	}

	if err := h.errorReportRepo.MarkAsProcessed(reportID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "更新错误报告状态时发生错误",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "错误报告已标记为处理",
	})
}

// GetErrorStats 获取错误统计信息 - 对应JS版本的 GET /api/errors/stats
func (h *ErrorReportHandler) GetErrorStats(c *gin.Context) {
	stats, err := h.errorReportRepo.GetErrorStats()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "获取错误统计信息时发生错误",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    stats,
	})
}