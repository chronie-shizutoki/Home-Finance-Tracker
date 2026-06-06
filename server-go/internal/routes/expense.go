package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handlers"
	"homemoney/internal/repository"
)

// SetupExpenseRoutes 设置消费记录相关路由 - 与Node.js版本完全一致
func SetupExpenseRoutes(router *gin.Engine, expenseRepo *repository.ExpenseRepository) {
	expenseHandler := handlers.NewExpenseHandler(expenseRepo)

	// 创建路由组
	api := router.Group("/api")
	{
		// 消费记录路由组
		expenses := api.Group("/expenses")
		{
			// 获取消费记录列表（支持分页、筛选、排序）
			expenses.GET("/", expenseHandler.GetExpenses)

			// 按日期分组获取消费记录
			expenses.GET("/by-date", expenseHandler.GetExpensesByDate)

			// 创建新的消费记录
			expenses.POST("/", expenseHandler.CreateExpense)

			// 同步消费记录
			expenses.POST("/sync", expenseHandler.SyncExpenses)

			// 更新消费记录
			expenses.PUT("/:id", expenseHandler.UpdateExpense)

			// 软删除消费记录
			expenses.DELETE("/:id", expenseHandler.DeleteExpense)

			// 硬删除消费记录
			expenses.DELETE("/:id/hard", expenseHandler.HardDeleteExpense)
		}

		// 消费统计路由组
		expenseStats := api.Group("/expenses")
		{
			// 获取消费统计数据
			expenseStats.GET("/statistics", expenseHandler.GetExpenseStatistics)
		}
	}
}
