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
				"payments": []gin.H{
					{
						"endpoint": "/api/payments/donate",
						"method": "POST",
						"description": gin.H{
							"en": "Donation payment processing",
							"zh": "捐赠支付处理",
						},
						"usage": gin.H{
							"en": "Process user donation payment requests",
							"zh": "处理用户的捐赠支付请求",
						},
					},
					{
						"endpoint": "/api/payments/subscribe",
						"method": "POST",
						"description": gin.H{
							"en": "Membership subscription payment",
							"zh": "会员订阅支付",
						},
						"usage": gin.H{
							"en": "Process user membership subscription payment requests",
							"zh": "处理用户的会员订阅支付请求",
						},
					},
				},
				"json-files": []gin.H{
			{
				"endpoint": "/api/json-files/:filename",
				"method": "GET",
				"description": gin.H{
					"en": "Read JSON file content",
					"zh": "读取JSON文件内容",
				},
				"usage": gin.H{
					"en": "Replace :filename with the actual file name",
					"zh": "将:filename替换为实际的文件名",
				},
			},
			{
				"endpoint": "/api/json-files/:filename",
				"method": "POST",
				"description": gin.H{
					"en": "Write data to JSON file",
					"zh": "写入数据到JSON文件",
				},
				"usage": gin.H{
					"en": "Replace :filename and provide JSON data in body",
					"zh": "替换:filename并在请求体中提供JSON数据",
				},
			},
			{
				"endpoint": "/api/json-files",
				"method": "GET",
				"description": gin.H{
					"en": "Get list of all JSON files",
					"zh": "获取所有JSON文件列表",
				},
				"usage": gin.H{
					"en": "No parameters required",
					"zh": "无需参数",
				},
			},
			{
				"endpoint": "/api/json-files/:filename",
				"method": "DELETE",
				"description": gin.H{
					"en": "Delete a JSON file",
					"zh": "删除JSON文件",
				},
				"usage": gin.H{
					"en": "Replace :filename with the actual file name",
					"zh": "将:filename替换为实际的文件名",
				},
			},
			{
				"endpoint": "/api/json-files/:filename/info",
				"method": "GET",
				"description": gin.H{
					"en": "Get JSON file information",
					"zh": "获取JSON文件信息",
				},
				"usage": gin.H{
					"en": "Replace :filename with the actual file name",
					"zh": "将:filename替换为实际的文件名",
				},
			},
		},
		"logs": []gin.H{
			{
				"endpoint": "/api/logs",
				"method": "POST",
				"description": gin.H{
					"zh": "接收操作日志",
					"en": "Receive operation logs",
				},
				"usage": gin.H{
					"zh": "接收并异步保存前端发送的操作日志，支持用户行为、API请求、错误等多种日志类型。",
					"en": "Receive and asynchronously store operation logs sent from the frontend, supporting user actions, API requests, errors and other log types.",
				},
			},
			{
				"endpoint": "/api/logs",
				"method": "GET",
				"description": gin.H{
					"zh": "获取日志列表",
					"en": "Get logs list",
				},
				"usage": gin.H{
					"zh": "获取日志列表，支持分页（limit/offset）和筛选（type/startDate/endDate/username）。",
					"en": "Get log list with pagination (limit/offset) and filtering options (type/startDate/endDate/username).",
				},
			},
			{
				"endpoint": "/api/logs/stats",
				"method": "GET",
				"description": gin.H{
					"zh": "获取日志统计信息",
					"en": "Get log statistics",
				},
				"usage": gin.H{
					"zh": "获取不同类型日志的数量统计，可指定时间范围进行筛选。",
					"en": "Get statistics on the number of different log types, can filter by time range.",
				},
			},
			{
				"endpoint": "/api/logs/clean",
				"method": "DELETE",
				"description": gin.H{
					"zh": "清理过期日志",
					"en": "Clean expired logs",
				},
				"usage": gin.H{
					"zh": "清理指定天数之前的日志，默认保留45天。",
					"en": "Clean logs older than specified days, default to keeping logs for 45 days.",
				},
			},
		},
			},
		}

		c.JSON(http.StatusOK, apiHelp)
	})
}