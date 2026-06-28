package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// SetupErrorReportRoutes sets up error report related routes - fully consistent with JS version
func SetupErrorReportRoutes(router *gin.Engine, errorReportRepo *repository.ErrorReportRepository) {
	errorReportHandler := handler.NewErrorReportHandler(errorReportRepo)

	api := router.Group("/api")
	{
		// Submit error report (no authentication required)
		api.POST("/error/report", errorReportHandler.ReportError)

		// Get error report list
		api.GET("/errors", errorReportHandler.GetErrorReports)

		// Get error statistics
		api.GET("/errors/stats", errorReportHandler.GetErrorStats)

		// Mark error report as processed
		api.PUT("/errors/:reportId/process", errorReportHandler.ProcessErrorReport)
	}
}