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

// ImportHandler import handler
type ImportHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewImportHandler creates a new import handler
func NewImportHandler(expenseRepo *repository.ExpenseRepository) *ImportHandler {
	return &ImportHandler{expenseRepo: expenseRepo}
}

// getRecordKey generates a unique key for a record - fully consistent with JS version
func getRecordKey(record models.Expense) string {
	remark := ""
	if record.Remark != nil {
		remark = strings.TrimSpace(*record.Remark)
	}
	return fmt.Sprintf("%s_%s_%.0f_%s", record.Date, record.Type, record.Amount, remark)
}

// ImportExcel imports an Excel file - corresponds to JS version POST /api/import/excel
func (h *ImportHandler) ImportExcel(c *gin.Context) {
	file, err := c.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"message": "No file uploaded",
		})
		return
	}

	// Save temporary file
	tmpDir := filepath.Join(".", "uploads")
	if err := os.MkdirAll(tmpDir, 0755); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to create temporary directory",
		})
		return
	}
	tmpPath := filepath.Join(tmpDir, fmt.Sprintf("import_%d_%s", time.Now().UnixMilli(), file.Filename))
	if err := c.SaveUploadedFile(file, tmpPath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to save file",
		})
		return
	}
	defer func() { _ = os.Remove(tmpPath) }()

	// Read Excel file
	f, err := excelize.OpenFile(tmpPath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to read Excel file",
		})
		return
	}

	rows, err := f.GetRows(f.GetSheetName(0))
	if err != nil || len(rows) < 2 {
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"message": "No valid data to import.",
		})
		return
	}

	// Parse candidate records
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
			"message": "No valid data to import.",
		})
		return
	}

	// Step 1: Deduplicate duplicates within the Excel file - fully consistent with JS version
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

	// Step 2: Query existing records in database to check for duplicates
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

	// Step 3: Filter out truly new records to import
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
			"message": "All records already exist, no new data imported.",
			"stats": gin.H{
				"total":           len(candidateRecords),
				"skippedInternal": fileInternalDuplicates,
				"skippedExisting": duplicatesWithDatabase,
				"imported":        0,
			},
		})
		return
	}

	// Batch create
	if err := h.expenseRepo.BatchCreate(recordsToImport); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Database insertion failed.",
		})
		return
	}

	message := fmt.Sprintf("Successfully imported %d records.", len(recordsToImport))
	if fileInternalDuplicates > 0 || duplicatesWithDatabase > 0 {
		var skipDetails []string
		if fileInternalDuplicates > 0 {
			skipDetails = append(skipDetails, fmt.Sprintf("%d duplicate(s) within file", fileInternalDuplicates))
		}
		if duplicatesWithDatabase > 0 {
			skipDetails = append(skipDetails, fmt.Sprintf("%d already exist(s)", duplicatesWithDatabase))
		}
		message += fmt.Sprintf(" (Skipped: %s)", strings.Join(skipDetails, ", "))
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