package handler

import (
	"net/http"
	"strconv"

	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// LogHandler 日志处理器
type LogHandler struct {
	logService *service.LogService
}

// NewLogHandler 创建日志处理器实例
func NewLogHandler(logService *service.LogService) *LogHandler {
	return &LogHandler{logService: logService}
}

// ReceiveLog 接收前端发送的日志
// @Summary 接收操作日志
// @Description 接收并异步保存前端发送的操作日志
// @Tags logs
// @Accept json
// @Produce json
// @Param log body service.LogData true "日志数据"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs [post]
func (h *LogHandler) ReceiveLog(c *gin.Context) {
	var logData service.LogData

	// 绑定请求体
	if err := c.ShouldBindJSON(&logData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "请求体格式错误",
		})
		return
	}

	// 验证必要字段
	if logData.Timestamp == "" || logData.Type == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "参数错误",
		})
		return
	}

	// 异步处理日志，不阻塞响应
	h.logService.HandleLog(c, logData)

	// 立即返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "日志接收成功",
	})
}

// GetLogsList 获取日志列表
// @Summary 获取日志列表
// @Description 获取日志列表，支持分页和筛选
// @Tags logs
// @Produce json
// @Param limit query int false "每页数量" default(100)
// @Param offset query int false "偏移量" default(0)
// @Param type query string false "日志类型"
// @Param startDate query string false "开始日期"
// @Param endDate query string false "结束日期"
// @Param username query string false "用户名"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs [get]
func (h *LogHandler) GetLogsList(c *gin.Context) {
	var params service.QueryLogParams

	// 绑定查询参数
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "查询参数错误",
		})
		return
	}

	// 设置默认值
	if params.Limit <= 0 || params.Limit > 1000 {
		params.Limit = 100
	}
	if params.Offset < 0 {
		params.Offset = 0
	}

	// 查询日志
	logs, total, err := h.logService.GetLogs(params)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "服务器内部错误",
		})
		return
	}

	// 计算分页信息
	page := params.Offset/params.Limit + 1
	totalPages := (total + params.Limit - 1) / params.Limit

	// 返回结果
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "获取日志列表成功",
		"data": gin.H{
			"logs":       logs,
			"total":      total,
			"page":       page,
			"pageSize":   params.Limit,
			"totalPages": totalPages,
		},
	})
}

// GetLogStats 获取日志统计信息
// @Summary 获取日志统计信息
// @Description 获取日志统计信息，包括不同类型的日志数量
// @Tags logs
// @Produce json
// @Param startDate query string false "开始日期"
// @Param endDate query string false "结束日期"
// @Success 200 {object} map[string]interface{}
// @Router /api/logs/stats [get]
func (h *LogHandler) GetLogStats(c *gin.Context) {
	var params service.QueryLogParams

	// 绑定查询参数
	if err := c.ShouldBindQuery(&params); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "查询参数错误",
		})
		return
	}

	// 获取统计信息
	stats, err := h.logService.GetLogStats(params)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "获取日志统计失败",
		})
		return
	}

	// 返回结果
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "获取日志统计成功",
		"data":    stats,
	})
}

// CleanLogs 清理过期日志
// @Summary 清理过期日志
// @Description 清理指定天数之前的日志
// @Tags logs
// @Produce json
// @Param days query int false "保留天数" default(45)
// @Success 200 {object} map[string]interface{}
// @Router /api/logs/clean [delete]
func (h *LogHandler) CleanLogs(c *gin.Context) {
	// 获取保留天数参数
	daysStr := c.DefaultQuery("days", "45")
	days, err := strconv.Atoi(daysStr)
	if err != nil || days < 1 {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "清理日志参数错误",
		})
		return
	}

	// 清理日志
	deletedCount, err := h.logService.CleanLogs(days)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "清理日志失败",
		})
		return
	}

	// 返回结果
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "清理日志成功",
		"data": gin.H{
			"message":      "成功清理" + strconv.FormatInt(deletedCount, 10) + "条过期日志",
			"deletedCount": deletedCount,
			"keptDays":     days,
		},
	})
}
