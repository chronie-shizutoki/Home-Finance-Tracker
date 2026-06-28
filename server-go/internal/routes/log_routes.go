package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SetupLogRoutes sets up log related routes
// @Summary Setup log routes
// @Description Configure log related API endpoints
// @Tags Route configuration
func SetupLogRoutes(router *gin.RouterGroup, logService *service.LogService) {
	// Create log handler
	logHandler := handler.NewLogHandler(logService)
	
	// Log related route group
	logs := router.Group("/logs")
	{
		// Receive operation logs
		logs.POST("", logHandler.ReceiveLog)
		
		// Get log list
		logs.GET("", logHandler.GetLogsList)
		
		// Get log statistics
		logs.GET("/stats", logHandler.GetLogStats)
		
		// Clean expired logs
		logs.DELETE("/clean", logHandler.CleanLogs)
	}
}
