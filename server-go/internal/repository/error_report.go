package repository

import (
	"fmt"

	"homemoney/internal/models"

	"gorm.io/gorm"
)

// ErrorReportRepository 错误报告数据仓库
type ErrorReportRepository struct {
	db *gorm.DB
}

// NewErrorReportRepository 创建新的错误报告仓库
func NewErrorReportRepository(db *gorm.DB) *ErrorReportRepository {
	return &ErrorReportRepository{db: db}
}

// Create 创建错误报告
func (r *ErrorReportRepository) Create(report *models.ErrorReport) error {
	if report.ErrorType == "" || report.Message == "" {
		return fmt.Errorf("错误类型和错误消息是必填项")
	}
	return r.db.Create(report).Error
}

// FindByID 根据ID查找错误报告
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

// FindWithPagination 分页查找错误报告
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

// MarkAsProcessed 标记错误报告为已处理
func (r *ErrorReportRepository) MarkAsProcessed(id string) error {
	return r.db.Model(&models.ErrorReport{}).Where("id = ?", id).Update("is_processed", true).Error
}

// ErrorReportQueryParams 错误报告查询参数
type ErrorReportQueryParams struct {
	Page        int    `form:"page,default=1"`
	Limit       int    `form:"limit,default=20"`
	ErrorType   string `form:"errorType"`
	IsProcessed *bool  `form:"isProcessed"`
	DateFrom    string `form:"dateFrom"`
	DateTo      string `form:"dateTo"`
}