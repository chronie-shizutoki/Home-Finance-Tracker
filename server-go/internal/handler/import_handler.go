package handler

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"homemoney/internal/models"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
	"github.com/xuri/excelize/v2"
)

// ImportHandler 导入处理器
type ImportHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewImportHandler 创建新的导入处理器
func NewImportHandler(expenseRepo *repository.ExpenseRepository) *ImportHandler {
	return &ImportHandler{expenseRepo: expenseRepo}
}

// ImportExcel 导入Excel文件 - 对应JS版本的 POST /api/import/excel
func (h *ImportHandler) ImportExcel(c *gin.Context) {
	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "未上传文件",
		})
		return
	}

	// 保存临时文件
	tmpDir := filepath.Join(".", "uploads")
	os.MkdirAll(tmpDir, 0755)
	tmpPath := filepath.Join(tmpDir, fmt.Sprintf("import_%d_%s", time.Now().UnixMilli(), file.Filename))
	if err := c.SaveUploadedFile(file, tmpPath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "保存文件失败",
		})
		return
	}
	defer os.Remove(tmpPath)

	// 读取Excel文件
	f, err := excelize.OpenFile(tmpPath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "读取Excel文件失败",
		})
		return
	}

	rows, err := f.GetRows(f.GetSheetName(0))
	if err != nil || len(rows) < 2 {
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"message": "没有有效数据被导入。",
		})
		return
	}

	// 解析候选记录
	var candidateRecords []models.Expense
	headerRow := rows[0]
	for i := 1; i < len(rows); i++ {
		row := rows[i]
		record := models.Expense{}

		for j, cell := range row {
			if j >= len(headerRow) {
				break
			}
			header := headerRow[j]
			switch header {
			case "分类", "Category", "分類":
				record.Type = cell
			case "备注", "Description", "備註", "Notes":
				remark := cell
				record.Remark = &remark
			case "金额", "Amount", "金額":
				var amount float64
				fmt.Sscanf(cell, "%f", &amount)
				record.Amount = amount
			case "日期", "Date":
				record.Date = cell
			}
		}

		if record.Type != "" && record.Amount > 0 && record.Date != "" {
			record.UpdatedAt = time.Now().UnixMilli()
			record.Version = 1
			candidateRecords = append(candidateRecords, record)
		}
	}

	if len(candidateRecords) == 0 {
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"message": "没有有效数据被导入。",
		})
		return
	}

	// 批量创建（跳过重复）
	serverChanges, _, err := h.expenseRepo.SyncExpenses(nil, candidateRecords, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "数据库插入失败。",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": fmt.Sprintf("成功导入 %d 条记录。", len(serverChanges)),
		"stats": gin.H{
			"total":    len(candidateRecords),
			"imported": len(serverChanges),
		},
	})
}