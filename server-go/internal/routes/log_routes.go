package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SetupLogRoutes 设置日志相关路由
// @Summary 设置日志路由
// @Description 配置日志相关的API端点
// @Tags 路由配置
func SetupLogRoutes(router *gin.RouterGroup, logService *service.LogService) {
	// 创建日志处理器
	logHandler := handler.NewLogHandler(logService)
	
	// 日志相关路由组
	logs := router.Group("/logs")
	{
		// 接收操作日志
		logs.POST("", logHandler.ReceiveLog)
		
		// 获取日志列表
		logs.GET("", logHandler.GetLogsList)
		
		// 获取日志统计信息
		logs.GET("/stats", logHandler.GetLogStats)
		
		// 清理过期日志
		logs.DELETE("/clean", logHandler.CleanLogs)
	}
}
