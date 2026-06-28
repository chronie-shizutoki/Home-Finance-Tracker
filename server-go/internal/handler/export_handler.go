package handler

import (
	"fmt"
	"net/http"
	"time"

	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
	"github.com/xuri/excelize/v2"
)

// ExportHandler export handler
type ExportHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewExportHandler creates a new export handler
func NewExportHandler(expenseRepo *repository.ExpenseRepository) *ExportHandler {
	return &ExportHandler{expenseRepo: expenseRepo}
}

// ExportExcel exports an Excel file - corresponds to JS version GET /api/export/excel
func (h *ExportHandler) ExportExcel(c *gin.Context) {
	lang := c.DefaultQuery("lang", "zh-CN")

	// Get all expense records
	expenses, err := h.expenseRepo.FindAll()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Export failed",
		})
		return
	}

	// Set headers
	headers := map[string]map[string]string{
		"zh-CN": {"date": "日期", "type": "分类", "amount": "金额", "remark": "备注"},
		"en-US": {"date": "Date", "type": "Category", "amount": "Amount", "remark": "Notes"},
		"zh-TW": {"date": "日期", "type": "分類", "amount": "金額", "remark": "備註"},
	}

	header, ok := headers[lang]
	if !ok {
		header = headers["zh-CN"]
	}

	// Create Excel file
	f := excelize.NewFile()
	sheet := "Expenses"
	_ = f.SetSheetName("Sheet1", sheet)

	// Write header row
	_ = f.SetCellValue(sheet, "A1", header["date"])
	_ = f.SetCellValue(sheet, "B1", header["type"])
	_ = f.SetCellValue(sheet, "C1", header["amount"])
	_ = f.SetCellValue(sheet, "D1", header["remark"])

	// Write data
	for i, expense := range expenses {
		row := i + 2
		_ = f.SetCellValue(sheet, fmt.Sprintf("A%d", row), expense.Date)
		_ = f.SetCellValue(sheet, fmt.Sprintf("B%d", row), expense.Type)
		_ = f.SetCellValue(sheet, fmt.Sprintf("C%d", row), expense.Amount)
		if expense.Remark != nil {
			_ = f.SetCellValue(sheet, fmt.Sprintf("D%d", row), *expense.Remark)
		}
	}

	// Set column widths
	_ = f.SetColWidth(sheet, "A", "A", 12)
	_ = f.SetColWidth(sheet, "B", "B", 15)
	_ = f.SetColWidth(sheet, "C", "C", 10)
	_ = f.SetColWidth(sheet, "D", "D", 30)

	// Generate filename
	timestamp := time.Now().Format("20060102150405")
	filename := fmt.Sprintf("expenses_%s.xlsx", timestamp)

	// Set response headers
	c.Header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%s", filename))

	// Write response
	if err := f.Write(c.Writer); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to generate Excel file",
		})
		return
	}
}