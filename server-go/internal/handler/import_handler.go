package handler

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
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

// getRecordKey 生成记录的唯一键 - 与JS版本完全一致
func getRecordKey(record models.Expense) string {
	remark := ""
	if record.Remark != nil {
		remark = strings.TrimSpace(*record.Remark)
	}
	return fmt.Sprintf("%s_%s_%.0f_%s", record.Date, record.Type, record.Amount, remark)
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
	if err := os.MkdirAll(tmpDir, 0755); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "创建临时目录失败",
		})
		return
	}
	tmpPath := filepath.Join(tmpDir, fmt.Sprintf("import_%d_%s", time.Now().UnixMilli(), file.Filename))
	if err := c.SaveUploadedFile(file, tmpPath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "保存文件失败",
		})
		return
	}
	defer func() { _ = os.Remove(tmpPath) }()

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
				_, _ = fmt.Sscanf(strings.Map(func(r rune) rune {
					if (r >= '0' && r <= '9') || r == '.' {
						return r
					}
					return -1
				}, cell), "%f", &amount)
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

	// 步骤1: 去重Excel文件内部的重复记录 - 与JS版本完全一致
	uniqueRecordsMap := make(map[string]models.Expense)
	for _, record := range candidateRecords {
		key := getRecordKey(record)
		if _, exists := uniqueRecordsMap[key]; !exists {
			uniqueRecordsMap[key] = record
		}
	}
	var uniqueInFile []models.Expense
	for _, record := range uniqueRecordsMap {
		uniqueInFile = append(uniqueInFile, record)
	}
	fileInternalDuplicates := len(candidateRecords) - len(uniqueInFile)

	// 步骤2: 查询数据库中的现有记录，检查是否重复
	existingKeys := make(map[string]bool)
	if len(uniqueInFile) > 0 {
		uniqueDates := make(map[string]bool)
		var dates []string
		for _, r := range uniqueInFile {
			if !uniqueDates[r.Date] {
				uniqueDates[r.Date] = true
				dates = append(dates, r.Date)
			}
		}
		existingRecords, err := h.expenseRepo.FindByDates(dates)
		if err == nil {
			for _, record := range existingRecords {
				existingKeys[getRecordKey(record)] = true
			}
		}
	}

	// 步骤3: 过滤出真正要导入的新记录
	var recordsToImport []models.Expense
	for _, record := range uniqueInFile {
		if !existingKeys[getRecordKey(record)] {
			recordsToImport = append(recordsToImport, record)
		}
	}
	duplicatesWithDatabase := len(uniqueInFile) - len(recordsToImport)

	if len(recordsToImport) == 0 {
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"message": "所有记录都已存在，没有新数据导入。",
			"stats": gin.H{
				"total":           len(candidateRecords),
				"skippedInternal": fileInternalDuplicates,
				"skippedExisting": duplicatesWithDatabase,
				"imported":        0,
			},
		})
		return
	}

	// 批量创建
	if err := h.expenseRepo.BatchCreate(recordsToImport); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "数据库插入失败。",
		})
		return
	}

	message := fmt.Sprintf("成功导入 %d 条记录。", len(recordsToImport))
	if fileInternalDuplicates > 0 || duplicatesWithDatabase > 0 {
		var skipDetails []string
		if fileInternalDuplicates > 0 {
			skipDetails = append(skipDetails, fmt.Sprintf("%d 条文件内重复", fileInternalDuplicates))
		}
		if duplicatesWithDatabase > 0 {
			skipDetails = append(skipDetails, fmt.Sprintf("%d 条已存在", duplicatesWithDatabase))
		}
		message += fmt.Sprintf(" (跳过: %s)", strings.Join(skipDetails, ", "))
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": message,
		"stats": gin.H{
			"total":           len(candidateRecords),
			"skippedInternal": fileInternalDuplicates,
			"skippedExisting": duplicatesWithDatabase,
			"imported":        len(recordsToImport),
		},
	})
}