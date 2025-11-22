package repository

import (
	"fmt"

	"homemoney/internal/models"

	"gorm.io/gorm"
)

// ExpenseRepository 消费记录数据仓库
type ExpenseRepository struct {
	db *gorm.DB
}

// NewExpenseRepository 创建新的消费记录仓库
func NewExpenseRepository(db *gorm.DB) *ExpenseRepository {
	return &ExpenseRepository{
		db: db,
	}
}

// Create 创建消费记录
func (r *ExpenseRepository) Create(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("验证失败: %w", err)
	}
	return r.db.Create(expense).Error
}

// FindByID 根据ID查找消费记录
func (r *ExpenseRepository) FindByID(id string) (*models.Expense, error) {
	var expense models.Expense
	if err := r.db.First(&expense, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &expense, nil
}

// FindWithPagination 分页查找消费记录
func (r *ExpenseRepository) FindWithPagination(query *models.ExpenseQuery) ([]models.Expense, int64, error) {
	var expenses []models.Expense
	var total int64

	// 验证查询参数
	if err := query.Validate(); err != nil {
		return nil, 0, fmt.Errorf("查询参数验证失败: %w", err)
	}

	// 构建基础查询
	baseQuery := r.db.Model(&models.Expense{})

	// 应用查询条件
	query.ApplyToQuery(baseQuery)

	// 计算总数
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	// 应用排序和分页
	query.ApplySort(baseQuery)
	baseQuery = baseQuery.Offset(query.Offset).Limit(query.Limit)

	// 执行查询
	if err := baseQuery.Find(&expenses).Error; err != nil {
		return nil, 0, err
	}

	return expenses, total, nil
}

// Delete 删除消费记录
func (r *ExpenseRepository) Delete(id string) error {
	result := r.db.Delete(&models.Expense{}, "id = ?", id)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("记录不存在")
	}
	return nil
}

// GetStatistics 获取统计数据
func (r *ExpenseRepository) GetStatistics(query *models.ExpenseQuery) (*models.ExpenseStats, error) {
	if query != nil {
		if err := query.Validate(); err != nil {
			return nil, fmt.Errorf("查询参数验证失败: %w", err)
		}
	}
	return models.GetStatsWithSQL(r.db, query)
}

// GetMeta 获取元数据
func (r *ExpenseRepository) GetMeta() (*models.ExpenseMeta, error) {
	var meta models.ExpenseMeta

	// 获取唯一类型
	var uniqueTypes []string
	if err := r.db.Model(&models.Expense{}).Distinct().Pluck("type", &uniqueTypes).Error; err != nil {
		return nil, err
	}
	meta.UniqueTypes = uniqueTypes

	// 获取可用月份 - 由于date现在是字符串类型，使用字符串截取方式获取年月
	var availableMonths []string
	if err := r.db.Model(&models.Expense{}).
		Order("date DESC").
		Distinct().
		Pluck("SUBSTRING(date, 1, 7)", &availableMonths).Error; err != nil {
		return nil, fmt.Errorf("获取月份数据失败: %w", err)
	}
	meta.AvailableMonths = availableMonths

	return &meta, nil
}

// Exists 检查记录是否存在
func (r *ExpenseRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Expense{}).Where("id = ?", id).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

// BatchCreate 批量创建消费记录
func (r *ExpenseRepository) BatchCreate(expenses []models.Expense) error {
	if len(expenses) == 0 {
		return nil
	}

	// 验证所有记录
	for i, expense := range expenses {
		if err := expense.Validate(); err != nil {
			return fmt.Errorf("第%d条记录验证失败: %w", i+1, err)
		}
	}

	// 分批处理，每批50条记录
	batchSize := 50
	for i := 0; i < len(expenses); i += batchSize {
		end := i + batchSize
		if end > len(expenses) {
			end = len(expenses)
		}

		batch := expenses[i:end]
		if err := r.db.CreateInBatches(batch, batchSize).Error; err != nil {
			return fmt.Errorf("第%d批数据创建失败: %w", i/batchSize+1, err)
		}
	}
	return nil
}

// Update 更新消费记录
func (r *ExpenseRepository) Update(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("验证失败: %w", err)
	}
	return r.db.Save(expense).Error
}

// FindAll 获取所有消费记录（用于迁移测试）
func (r *ExpenseRepository) FindAll() ([]models.Expense, error) {
	var expenses []models.Expense
	// 由于date是字符串类型，直接按字符串排序
	if err := r.db.Order("date DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}
