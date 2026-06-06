package repository

import (
	"fmt"
	"time"

	"homemoney/internal/models"

	"github.com/gin-gonic/gin"
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

// Delete 软删除消费记录（设置deletedAt）
func (r *ExpenseRepository) Delete(id string) error {
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Update("deletedAt", now)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("记录不存在")
	}
	return nil
}

// HardDelete 硬删除消费记录
func (r *ExpenseRepository) HardDelete(id string) error {
	result := r.db.Where("id = ?", id).Delete(&models.Expense{})
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

// Exists 检查记录是否存在（未删除的）
func (r *ExpenseRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Count(&count).Error; err != nil {
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

// FindByDates 根据日期列表查找记录 - 用于导入去重
func (r *ExpenseRepository) FindByDates(dates []string) ([]models.Expense, error) {
	if len(dates) == 0 {
		return nil, nil
	}
	var expenses []models.Expense
	if err := r.db.Where("date IN ? AND deletedAt IS NULL", dates).Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}

// Update 更新消费记录
func (r *ExpenseRepository) Update(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("验证失败: %w", err)
	}
	return r.db.Save(expense).Error
}

// SyncExpenses 同步消费记录 - 与JS版本完全一致
func (r *ExpenseRepository) SyncExpenses(lastSyncTime *int64, changes []models.Expense, localIDs []string) ([]models.Expense, []gin.H, error) {
	serverChanges := make([]models.Expense, 0)
	conflicts := make([]gin.H, 0)

	// 如果客户端提供了localIDs，返回客户端缺失的记录
	if len(localIDs) > 0 {
		// 获取所有服务器记录ID
		var allServerRecords []models.Expense
		if err := r.db.Model(&models.Expense{}).Select("id").Find(&allServerRecords).Error; err != nil {
			return nil, nil, fmt.Errorf("获取服务器记录失败: %w", err)
		}

		allServerIDs := make(map[string]bool)
		for _, r := range allServerRecords {
			allServerIDs[r.ID] = true
		}
		localIDSet := make(map[string]bool)
		for _, id := range localIDs {
			localIDSet[id] = true
		}

		// 找出服务器有但客户端没有的记录
		var missingIDs []string
		for id := range allServerIDs {
			if !localIDSet[id] {
				missingIDs = append(missingIDs, id)
			}
		}

		if len(missingIDs) > 0 {
			if err := r.db.Where("id IN ?", missingIDs).Find(&serverChanges).Error; err != nil {
				return nil, nil, fmt.Errorf("获取缺失记录失败: %w", err)
			}
		}
	} else if lastSyncTime != nil && *lastSyncTime > 0 {
		// 旧行为：返回lastSyncTime之后更新的记录
		if err := r.db.Where("updatedAt > ?", *lastSyncTime).Order("updatedAt ASC").Find(&serverChanges).Error; err != nil {
			return nil, nil, fmt.Errorf("获取更新记录失败: %w", err)
		}
	}

	// 处理客户端提交的变更
	for _, change := range changes {
		if change.DeletedAt != nil {
			// 删除操作
			var serverRecord models.Expense
			err := r.db.Where("id = ?", change.ID).First(&serverRecord).Error
			if err == nil {
				r.db.Model(&serverRecord).Updates(map[string]interface{}{
					"deletedAt": *change.DeletedAt,
					"updatedAt": change.UpdatedAt,
				})
			}
			continue
		}

		var serverRecord models.Expense
		err := r.db.Where("id = ?", change.ID).First(&serverRecord).Error
		if err == gorm.ErrRecordNotFound {
			// 创建新记录
			if createErr := r.Create(&change); createErr != nil {
				fmt.Printf("Error processing change: %s, %v\n", change.ID, createErr)
			}
		} else if err != nil {
			fmt.Printf("Error processing change: %s, %v\n", change.ID, err)
		} else {
			if change.UpdatedAt > serverRecord.UpdatedAt {
				// 客户端版本更新，更新服务器
				r.db.Model(&serverRecord).Updates(map[string]interface{}{
					"type":      change.Type,
					"remark":    change.Remark,
					"amount":    change.Amount,
					"date":      change.Date,
					"version":   change.Version,
					"updatedAt": change.UpdatedAt,
				})
			} else if change.UpdatedAt < serverRecord.UpdatedAt {
				// 冲突
				conflicts = append(conflicts, gin.H{
					"id":              change.ID,
					"clientVersion":   change.Version,
					"serverVersion":   serverRecord.Version,
					"clientUpdatedAt": change.UpdatedAt,
					"serverUpdatedAt": serverRecord.UpdatedAt,
					"serverData":      serverRecord,
				})
			}
		}
	}

	return serverChanges, conflicts, nil
}

// GetExpensesByDate 按日期分组获取消费记录
func (r *ExpenseRepository) GetExpensesByDate(query *models.ExpenseQuery) (map[string][]models.Expense, int64, error) {
	var expenses []models.Expense
	var total int64

	baseQuery := r.db.Model(&models.Expense{})
	query.ApplyToQuery(baseQuery)

	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	query.ApplySort(baseQuery)
	if err := baseQuery.Find(&expenses).Error; err != nil {
		return nil, 0, err
	}

	// 按日期分组
	grouped := make(map[string][]models.Expense)
	for _, e := range expenses {
		grouped[e.Date] = append(grouped[e.Date], e)
	}

	return grouped, total, nil
}

// FindAll 获取所有消费记录（用于迁移测试）
func (r *ExpenseRepository) FindAll() ([]models.Expense, error) {
	var expenses []models.Expense
	if err := r.db.Where("deletedAt IS NULL").Order("date DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}
