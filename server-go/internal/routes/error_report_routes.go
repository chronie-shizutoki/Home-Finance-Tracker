package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// SetupErrorReportRoutes 设置错误报告相关路由 - 与JS版本完全一致
func SetupErrorReportRoutes(router *gin.Engine, errorReportRepo *repository.ErrorReportRepository) {
	errorReportHandler := handler.NewErrorReportHandler(errorReportRepo)

	api := router.Group("/api")
	{
		// 提交错误报告（无需认证）
		api.POST("/error/report", errorReportHandler.ReportError)

		// 获取错误报告列表
		api.GET("/errors", errorReportHandler.GetErrorReports)

		// 标记错误报告为已处理
		api.PUT("/errors/:reportId/process", errorReportHandler.ProcessErrorReport)
	}
}