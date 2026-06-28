package repository

import (
	"fmt"
	"time"

	"homemoney/internal/models"

	"gorm.io/gorm"
)

// ErrorReportRepository error report data repository
type ErrorReportRepository struct {
	db *gorm.DB
}

// NewErrorReportRepository creates a new error report repository
func NewErrorReportRepository(db *gorm.DB) *ErrorReportRepository {
	return &ErrorReportRepository{db: db}
}

// Create creates an error report
func (r *ErrorReportRepository) Create(report *models.ErrorReport) error {
	if report.ErrorType == "" || report.Message == "" {
		return fmt.Errorf("error type and error message are required")
	}
	return r.db.Create(report).Error
}

// FindByID finds an error report by ID
func (r *ErrorReportRepository) FindByID(id string) (*models.ErrorReport, error) {
	var report models.ErrorReport
	if err := r.db.First(&report, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &report, nil
}

// FindWithPagination finds error reports with pagination
func (r *ErrorReportRepository) FindWithPagination(params ErrorReportQueryParams) ([]models.ErrorReport, int64, error) {
	var reports []models.ErrorReport
	var total int64

	query := r.db.Model(&models.ErrorReport{})

	if params.ErrorType != "" {
		query = query.Where("error_type = ?", params.ErrorType)
	}
	if params.IsProcessed != nil {
		query = query.Where("is_processed = ?", *params.IsProcessed)
	}
	if params.DateFrom != "" {
		query = query.Where("created_at >= ?", params.DateFrom)
	}
	if params.DateTo != "" {
		query = query.Where("created_at <= ?", params.DateTo)
	}

	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (params.Page - 1) * params.Limit
	if err := query.Order("created_at DESC").Offset(offset).Limit(params.Limit).Find(&reports).Error; err != nil {
		return nil, 0, err
	}

	return reports, total, nil
}

// MarkAsProcessed marks an error report as processed
func (r *ErrorReportRepository) MarkAsProcessed(id string) error {
	return r.db.Model(&models.ErrorReport{}).Where("id = ?", id).Update("is_processed", true).Error
}

// ErrorStats error statistics info
type ErrorStats struct {
	ByType      []ErrorTypeStat   `json:"byType"`
	ByStatus    []ErrorStatusStat `json:"byStatus"`
	Last24Hours int64             `json:"last24Hours"`
}

// ErrorTypeStat error type statistics
type ErrorTypeStat struct {
	ErrorType string `json:"errorType"`
	Count     int64  `json:"count"`
}

// ErrorStatusStat error status statistics
type ErrorStatusStat struct {
	IsProcessed bool  `json:"isProcessed"`
	Count       int64 `json:"count"`
}

// GetErrorStats gets error statistics
func (r *ErrorReportRepository) GetErrorStats() (*ErrorStats, error) {
	stats := &ErrorStats{}

	// By error type
	if err := r.db.Model(&models.ErrorReport{}).
		Select("error_type, COUNT(id) as count").
		Group("error_type").
		Scan(&stats.ByType).Error; err != nil {
		return nil, err
	}

	// By processing status
	if err := r.db.Model(&models.ErrorReport{}).
		Select("is_processed, COUNT(id) as count").
		Group("is_processed").
		Scan(&stats.ByStatus).Error; err != nil {
		return nil, err
	}

	// Error count in the last 24 hours
	last24Hours := time.Now().Add(-24 * time.Hour)
	if err := r.db.Model(&models.ErrorReport{}).
		Where("created_at >= ?", last24Hours).
		Count(&stats.Last24Hours).Error; err != nil {
		return nil, err
	}

	return stats, nil
}

// ErrorReportQueryParams error report query parameters
type ErrorReportQueryParams struct {
	Page        int    `form:"page,default=1"`
	Limit       int    `form:"limit,default=20"`
	ErrorType   string `form:"errorType"`
	IsProcessed *bool  `form:"isProcessed"`
	DateFrom    string `form:"dateFrom"`
	DateTo      string `form:"dateTo"`
}