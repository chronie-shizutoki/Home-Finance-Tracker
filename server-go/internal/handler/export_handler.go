package handler

import (
	"fmt"
	"net/http"
	"time"

	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
	"github.com/xuri/excelize/v2"
)

// ExportHandler 导出处理器
type ExportHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewExportHandler 创建新的导出处理器
func NewExportHandler(expenseRepo *repository.ExpenseRepository) *ExportHandler {
	return &ExportHandler{expenseRepo: expenseRepo}
}

// ExportExcel 导出Excel文件 - 对应JS版本的 GET /api/export/excel
func (h *ExportHandler) ExportExcel(c *gin.Context) {
	lang := c.DefaultQuery("lang", "zh-CN")

	// 获取所有消费记录
	expenses, err := h.expenseRepo.FindAll()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "导出失败",
		})
		return
	}

	// 设置表头
	headers := map[string]map[string]string{
		"zh-CN": {"date": "日期", "type": "分类", "amount": "金额", "remark": "备注"},
		"en-US": {"date": "Date", "type": "Category", "amount": "Amount", "remark": "Notes"},
		"zh-TW": {"date": "日期", "type": "分類", "amount": "金額", "remark": "備註"},
	}

	header, ok := headers[lang]
	if !ok {
		header = headers["zh-CN"]
	}

	// 创建Excel文件
	f := excelize.NewFile()
	sheet := "Expenses"
	f.SetSheetName("Sheet1", sheet)

	// 写入表头
	f.SetCellValue(sheet, "A1", header["date"])
	f.SetCellValue(sheet, "B1", header["type"])
	f.SetCellValue(sheet, "C1", header["amount"])
	f.SetCellValue(sheet, "D1", header["remark"])

	// 写入数据
	for i, expense := range expenses {
		row := i + 2
		f.SetCellValue(sheet, fmt.Sprintf("A%d", row), expense.Date)
		f.SetCellValue(sheet, fmt.Sprintf("B%d", row), expense.Type)
		f.SetCellValue(sheet, fmt.Sprintf("C%d", row), expense.Amount)
		if expense.Remark != nil {
			f.SetCellValue(sheet, fmt.Sprintf("D%d", row), *expense.Remark)
		}
	}

	// 设置列宽
	f.SetColWidth(sheet, "A", "A", 12)
	f.SetColWidth(sheet, "B", "B", 15)
	f.SetColWidth(sheet, "C", "C", 10)
	f.SetColWidth(sheet, "D", "D", 30)

	// 生成文件名
	timestamp := time.Now().Format("20060102150405")
	filename := fmt.Sprintf("expenses_%s.xlsx", timestamp)

	// 设置响应头
	c.Header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%s", filename))

	// 写入响应
	if err := f.Write(c.Writer); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "生成Excel文件失败",
		})
		return
	}
}