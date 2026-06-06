package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
)

// SetupExportRoutes 设置导出/导入相关路由 - 与JS版本完全一致
func SetupExportRoutes(router *gin.Engine, expenseRepo *repository.ExpenseRepository) {
	exportHandler := handler.NewExportHandler(expenseRepo)
	importHandler := handler.NewImportHandler(expenseRepo)

	// 导出Excel
	router.GET("/api/export/excel", exportHandler.ExportExcel)

	// 导入Excel
	router.POST("/api/import/excel", importHandler.ImportExcel)
}