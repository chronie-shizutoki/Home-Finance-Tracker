package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handlers"
	"homemoney/internal/repository"
)

// SetupExpenseRoutes sets up expense record related routes - fully consistent with Node.js version
func SetupExpenseRoutes(router *gin.Engine, expenseRepo *repository.ExpenseRepository) {
	expenseHandler := handlers.NewExpenseHandler(expenseRepo)

	// Create route group
	api := router.Group("/api")
	{
		// Expense record route group
		expenses := api.Group("/expenses")
		{
			// Get expense record list (supports pagination, filtering, sorting)
			expenses.GET("/", expenseHandler.GetExpenses)

			// Get expense records grouped by date
			expenses.GET("/by-date", expenseHandler.GetExpensesByDate)

			// Create new expense record
			expenses.POST("/", expenseHandler.CreateExpense)

			// Sync expense records
			expenses.POST("/sync", expenseHandler.SyncExpenses)

			// Update expense record
			expenses.PUT("/:id", expenseHandler.UpdateExpense)

			// Soft delete expense record
			expenses.DELETE("/:id", expenseHandler.DeleteExpense)

			// Hard delete expense record
			expenses.DELETE("/:id/hard", expenseHandler.HardDeleteExpense)
		}

		// Expense statistics route group
		expenseStats := api.Group("/expenses")
		{
			// Get expense statistics
			expenseStats.GET("/statistics", expenseHandler.GetExpenseStatistics)
		}
	}
}
