package models

import (
	"errors"
	"fmt"
	"math"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Expense 消费记录
type Expense struct {
	ID      uuid.UUID `json:"id" gorm:"type:char(36);primaryKey"`
	Type    string    `json:"type" gorm:"type:varchar(100);not null"`
	Remark  *string   `json:"remark,omitempty" gorm:"type:text"`
	Amount  float64   `json:"amount" gorm:"type:decimal(10,2);not null"`
	Time    time.Time `json:"time" gorm:"type:datetime;not null;index"`
	
	// 时间戳
	CreatedAt time.Time      `json:"created_at"`
	UpdatedAt time.Time      `json:"updated_at"`
	DeletedAt gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName 指定表名
func (Expense) TableName() string {
	return "expenses"
}

// BeforeCreate GORM钩子
func (e *Expense) BeforeCreate(tx *gorm.DB) error {
	if e.ID == uuid.Nil {
		e.ID = uuid.New()
	}
	return nil
}

// ExpenseQuery 消费记录查询条件
type ExpenseQuery struct {
	Keyword   string     `form:"keyword"`
	Type      string     `form:"type"`
	Month     string     `form:"month"`
	StartDate *time.Time `form:"startDate"`
	EndDate   *time.Time `form:"endDate"`
	MinAmount *float64   `form:"minAmount"`
	MaxAmount *float64   `form:"maxAmount"`
	Limit     int        `form:"limit,default=20"`
	Offset    int        `form:"offset,default=0"`
	Sort      string     `form:"sort,default=dateDesc"`
}

// ExpenseMeta 元数据
type ExpenseMeta struct {
	UniqueTypes     []string `json:"unique_types"`
	AvailableMonths []string `json:"available_months"`
}

// ExpenseStats 消费统计 - 与JS版本完全兼容
type ExpenseStats struct {
	Count           int                  `json:"count" binding:"required"`
	TotalAmount     float64              `json:"totalAmount" binding:"required"`
	AverageAmount   float64              `json:"averageAmount" binding:"required"`
	MedianAmount    float64              `json:"medianAmount" binding:"required"`
	MinAmount       float64              `json:"minAmount" binding:"required"`
	MaxAmount       float64              `json:"maxAmount" binding:"required"`
	TypeDistribution map[string]TypeDistributionItem `json:"typeDistribution" binding:"required"`
}

// TypeDistributionItem 类型分布统计项
type TypeDistributionItem struct {
	Count     int     `json:"count"`
	Amount    float64 `json:"amount"`
	Percentage int    `json:"percentage"`
}

// Validate 验证字段
func (e *Expense) Validate() error {
	if e.Type == "" {
		return errors.New("消费类型不能为空")
	}
	if e.Amount <= 0 {
		return errors.New("消费金额必须大于0")
	}
	if e.Time.IsZero() {
		return errors.New("消费时间不能为空")
	}
	return nil
}

// ValidateQuery 验证查询参数
func (q *ExpenseQuery) Validate() error {
	// 验证排序参数
	validSorts := map[string]bool{
		"dateAsc":    true,
		"dateDesc":   true,
		"amountAsc":  true,
		"amountDesc": true,
	}
	if q.Sort != "" && !validSorts[q.Sort] {
		return fmt.Errorf("无效的排序参数: %s", q.Sort)
	}
	
	// 验证分页参数
	if q.Limit < 1 || q.Limit > 100 {
		return errors.New("limit参数必须在1-100之间")
	}
	if q.Offset < 0 {
		return errors.New("offset参数不能为负数")
	}
	
	// 验证日期范围
	if q.StartDate != nil && q.EndDate != nil && q.StartDate.After(*q.EndDate) {
		return errors.New("开始日期不能晚于结束日期")
	}
	
	// 验证金额范围
	if q.MinAmount != nil && *q.MinAmount < 0 {
		return errors.New("最小金额不能为负数")
	}
	if q.MaxAmount != nil && *q.MaxAmount < 0 {
		return errors.New("最大金额不能为负数")
	}
	if q.MinAmount != nil && q.MaxAmount != nil && *q.MinAmount > *q.MaxAmount {
		return errors.New("最小金额不能大于最大金额")
	}
	
	// 验证月份格式
	if q.Month != "" {
		if _, err := time.Parse("2006-01", q.Month); err != nil {
			return fmt.Errorf("月份格式错误，期望格式: YYYY-MM")
		}
	}
	
	return nil
}

// ToMonthRange 将月份转换为日期范围
func (q *ExpenseQuery) ToMonthRange() (time.Time, time.Time, error) {
	if q.Month == "" {
		return time.Time{}, time.Time{}, errors.New("月份不能为空")
	}
	
	parsed, err := time.Parse("2006-01", q.Month)
	if err != nil {
		return time.Time{}, time.Time{}, fmt.Errorf("月份解析失败: %w", err)
	}

	startDate := time.Date(parsed.Year(), parsed.Month(), 1, 0, 0, 0, 0, time.UTC)
	endDate := startDate.AddDate(0, 1, -1)
	endDate = time.Date(endDate.Year(), endDate.Month(), endDate.Day(), 23, 59, 59, 999999999, time.UTC)

	return startDate, endDate, nil
}

// ApplyToQuery 应用查询条件到GORM查询
func (q *ExpenseQuery) ApplyToQuery(db *gorm.DB) *gorm.DB {
	if q.Keyword != "" {
		keyword := "%" + q.Keyword + "%"
		db = db.Where("type LIKE ? OR remark LIKE ?", keyword, keyword)
	}
	if q.Type != "" {
		db = db.Where("type = ?", q.Type)
	}
	if q.StartDate != nil && q.EndDate != nil {
		db = db.Where("time BETWEEN ? AND ?", *q.StartDate, *q.EndDate)
	}
	if q.MinAmount != nil {
		db = db.Where("amount >= ?", *q.MinAmount)
	}
	if q.MaxAmount != nil {
		db = db.Where("amount <= ?", *q.MaxAmount)
	}
	return db
}

// ApplySort 应用排序
func (q *ExpenseQuery) ApplySort(db *gorm.DB) *gorm.DB {
	switch q.Sort {
	case "dateAsc":
		return db.Order("time ASC")
	case "dateDesc":
		return db.Order("time DESC")
	case "amountAsc":
		return db.Order("amount ASC")
	case "amountDesc":
		return db.Order("amount DESC")
	default:
		return db.Order("time DESC")
	}
}

// GetStatsWithSQL 使用原生SQL获取统计数据 - 与JS版本完全兼容
func GetStatsWithSQL(db *gorm.DB, query *ExpenseQuery) (*ExpenseStats, error) {
	stats := &ExpenseStats{
		TypeDistribution: make(map[string]TypeDistributionItem),
	}

	// 构建SQL查询
	sql := db.Model(&Expense{})
	
	// 应用查询条件
	query.ApplyToQuery(sql)

	// 获取总金额和数量
	var totalAmount float64
	var count int64
	
	if err := sql.Select("COALESCE(SUM(amount), 0)").Row().Scan(&totalAmount); err != nil {
		return nil, fmt.Errorf("获取总金额失败: %w", err)
	}
	stats.TotalAmount = totalAmount

	if err := sql.Count(&count).Error; err != nil {
		return nil, fmt.Errorf("获取总数失败: %w", err)
	}
	stats.Count = int(count)
	
	if count > 0 {
		stats.AverageAmount = stats.TotalAmount / float64(count)
	}

	// 获取所有记录用于统计（适用于小数据集）
	var allExpenses []Expense
	if err := query.ApplyToQuery(db.Model(&Expense{})).Find(&allExpenses).Error; err != nil {
		return nil, fmt.Errorf("获取消费记录失败: %w", err)
	}

	if len(allExpenses) == 0 {
		// 空数据时设置默认值
		stats.MedianAmount = 0
		stats.MinAmount = 0
		stats.MaxAmount = 0
		return stats, nil
	}

	// 计算最大值和最小值
	minAmount := allExpenses[0].Amount
	maxAmount := allExpenses[0].Amount
	
	// 按类型统计
	typeMap := make(map[string][]float64)
	for _, expense := range allExpenses {
		// 更新最大值和最小值
		if expense.Amount < minAmount {
			minAmount = expense.Amount
		}
		if expense.Amount > maxAmount {
			maxAmount = expense.Amount
		}
		
		typeMap[expense.Type] = append(typeMap[expense.Type], expense.Amount)
	}
	
	stats.MinAmount = minAmount
	stats.MaxAmount = maxAmount

	// 计算中位数
	var amounts []float64
	for _, expense := range allExpenses {
		amounts = append(amounts, expense.Amount)
	}
	
	// 排序
	for i := 0; i < len(amounts); i++ {
		for j := i + 1; j < len(amounts); j++ {
			if amounts[i] > amounts[j] {
				amounts[i], amounts[j] = amounts[j], amounts[i]
			}
		}
	}
	
	// 计算中位数
	if len(amounts) > 0 {
		if len(amounts)%2 == 0 {
			stats.MedianAmount = (amounts[len(amounts)/2-1] + amounts[len(amounts)/2]) / 2
		} else {
			stats.MedianAmount = amounts[len(amounts)/2]
		}
	}

	// 构建类型分布统计 - 与JS版本完全一致
	for expenseType, amounts := range typeMap {
		var typeTotal float64
		for _, amount := range amounts {
			typeTotal += amount
		}
		
		percentage := 0
		if int(count) > 0 && len(amounts) > 0 {
			percentage = int(math.Round(float64(len(amounts)) * 100.0 / float64(count)))
		}
		
		stats.TypeDistribution[expenseType] = TypeDistributionItem{
			Count:     len(amounts),
			Amount:    typeTotal,
			Percentage: percentage,
		}
	}

	return stats, nil
}