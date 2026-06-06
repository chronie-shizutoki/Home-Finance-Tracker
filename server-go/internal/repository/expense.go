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

// GetExpensesByDate 按日期分组获取消费记录 - 与JS版本getExpensesByDate完全一致
// 返回格式: [{date, count, totalAmount, expenses}, ...]
// 实现智能分页：确保同一个日期组的记录不会被拆分到不同页
func (r *ExpenseRepository) GetExpensesByDate(query *models.ExpenseQuery) ([]models.DateGroup, int64, *models.ExpenseMeta, error) {
	var allExpenses []models.Expense
	var total int64

	baseQuery := r.db.Model(&models.Expense{})
	query.ApplyToQuery(baseQuery)

	// 计算总数
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, nil, err
	}

	// 应用排序获取所有符合条件的数据
	query.ApplySort(baseQuery)
	if err := baseQuery.Find(&allExpenses).Error; err != nil {
		return nil, 0, nil, err
	}

	// 按日期分组
	groupedMap := make(map[string][]models.Expense)
	var dateOrder []string
	for _, e := range allExpenses {
		if _, exists := groupedMap[e.Date]; !exists {
			dateOrder = append(dateOrder, e.Date)
		}
		groupedMap[e.Date] = append(groupedMap[e.Date], e)
	}

	// 为每个日期组添加统计信息，与JS版本格式一致
	dateGroups := make([]models.DateGroup, 0, len(groupedMap))
	for _, date := range dateOrder {
		expenses := groupedMap[date]
		var totalAmount float64
		for _, exp := range expenses {
			totalAmount += exp.Amount
		}
		dateGroups = append(dateGroups, models.DateGroup{
			Date:        date,
			Count:       len(expenses),
			TotalAmount: totalAmount,
			Expenses:    expenses,
		})
	}

	// 根据排序类型对日期组进行排序 - 与JS版本一致
	isAmountSort := query.Sort == "amountAsc" || query.Sort == "amountDesc"
	if isAmountSort {
		// 按金额排序时，日期组按组内第一条记录的金额排序
		for i := 0; i < len(dateGroups); i++ {
			for j := i + 1; j < len(dateGroups); j++ {
				amountA := dateGroups[i].Expenses[0].Amount
				amountB := dateGroups[j].Expenses[0].Amount
				if query.Sort == "amountDesc" {
					if amountA < amountB {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				} else {
					if amountA > amountB {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				}
			}
		}
	} else {
		// 按日期排序
		for i := 0; i < len(dateGroups); i++ {
			for j := i + 1; j < len(dateGroups); j++ {
				if query.Sort == "dateDesc" {
					if dateGroups[i].Date < dateGroups[j].Date {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				} else {
					if dateGroups[i].Date > dateGroups[j].Date {
						dateGroups[i], dateGroups[j] = dateGroups[j], dateGroups[i]
					}
				}
			}
		}
	}

	// 智能分页：确保同一个日期组的记录不会被拆分到不同页
	// 按日期组进行分页
	pageSize := query.Limit
	if pageSize < 1 {
		pageSize = 10
	}
	var pages [][]models.DateGroup
	var currentPageData []models.DateGroup
	var currentRecordCount int

	for _, group := range dateGroups {
		groupRecordCount := group.Count
		// 如果当前页为空，或者添加这个组不会超过太多记录限制，则添加到当前页
		if currentRecordCount == 0 || currentRecordCount+groupRecordCount <= int(float64(pageSize)*1.5) {
			currentPageData = append(currentPageData, group)
			currentRecordCount += groupRecordCount
		} else {
			// 开始新的一页
			pages = append(pages, currentPageData)
			currentPageData = []models.DateGroup{group}
			currentRecordCount = groupRecordCount
		}
	}
	// 添加最后一页
	if len(currentPageData) > 0 {
		pages = append(pages, currentPageData)
	}

	// 获取请求的页码数据
	pageNum := 1
	if query.Limit > 0 {
		pageNum = query.Offset/query.Limit + 1
	}
	var pagedData []models.DateGroup
	if pageNum > 0 && pageNum <= len(pages) {
		pagedData = pages[pageNum-1]
	}

	// 获取元数据
	meta, err := r.GetMeta()
	if err != nil {
		meta = &models.ExpenseMeta{}
	}

	return pagedData, total, meta, nil
}

// FindAll 获取所有消费记录（用于迁移测试）
func (r *ExpenseRepository) FindAll() ([]models.Expense, error) {
	var expenses []models.Expense
	if err := r.db.Where("deletedAt IS NULL").Order("date DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}
