package routes

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

// SetupHelpRoutes 设置API帮助文档相关的路由
func SetupHelpRoutes(router *gin.Engine) {
	// 项目API帮助文档端点
	router.GET("/api", func(c *gin.Context) {
		apiHelp := gin.H{
			"apiVersion": "3.0.0",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
			"projectName": "Home Finance Tracker API (Go Version)",
			"description": "家庭财务管理系统后端API文档 - Go语言实现",
			"availableAPIs": gin.H{
				"base": []gin.H{
					{
						"endpoint": "/api/health",
						"method": "GET",
						"description": gin.H{
							"en": "System health check - returns detailed system status",
							"zh": "系统健康检查 - 返回详细的系统状态信息",
						},
						"usage": gin.H{
							"en": "Checks system status, database connection, and resource usage",
							"zh": "检查系统状态、数据库连接和资源使用情况",
						},
					},
					{
						"endpoint": "/api/health/lite",
						"method": "GET", 
						"description": gin.H{
							"en": "Lightweight health check - returns basic system status",
							"zh": "轻量级健康检查 - 返回基本的系统状态信息",
						},
						"usage": gin.H{
							"en": "Quick check of system and database status",
							"zh": "快速检查系统和数据库状态",
						},
					},
				},
				"expenses": []gin.H{
					{
						"endpoint": "/api/expenses",
						"method": "GET",
						"description": gin.H{
							"en": "Get expense records",
							"zh": "获取消费记录",
						},
						"usage": gin.H{
							"en": "Retrieve all expense records with optional filtering",
							"zh": "获取所有消费记录，支持筛选",
						},
					},
					{
						"endpoint": "/api/expenses",
						"method": "POST",
						"description": gin.H{
							"en": "Add new expense record",
							"zh": "添加新的消费记录",
						},
						"usage": gin.H{
							"en": "Create a new expense entry in the system",
							"zh": "在系统中创建新的消费记录",
						},
					},
					{
						"endpoint": "/api/expenses/:id",
						"method": "DELETE",
						"description": gin.H{
							"en": "Delete expense record",
							"zh": "删除消费记录",
						},
						"usage": gin.H{
							"en": "Remove an expense record by its ID",
							"zh": "通过ID删除消费记录",
						},
					},
					{
						"endpoint": "/api/expenses/statistics",
						"method": "GET",
						"description": gin.H{
							"en": "Get expense statistics",
							"zh": "获取消费统计信息",
						},
						"usage": gin.H{
							"en": "Retrieve statistical analysis of expense data",
							"zh": "获取消费数据的统计分析",
						},
					},
				},
			},
		}

		c.JSON(http.StatusOK, apiHelp)
	})
}