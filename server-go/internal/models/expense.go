package models

import (
	"errors"
	"fmt"
	"math"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Expense expense record - fully consistent with JS version
type Expense struct {
	ID        string  `json:"id" gorm:"type:varchar(36);primaryKey"`
	Type      string  `json:"type" gorm:"type:varchar(255);not null"`
	Remark    *string `json:"remark,omitempty" gorm:"type:varchar(255)"`
	Amount    float64 `json:"amount" gorm:"type:float;not null"`
	Date      string  `json:"date" gorm:"type:varchar(10);not null;index"`
	Version   int     `json:"version" gorm:"type:integer;not null;default:1"`
	UpdatedAt int64   `json:"updatedAt" gorm:"column:updatedAt;type:bigint;not null"`
	DeletedAt *int64  `json:"deletedAt,omitempty" gorm:"column:deletedAt;type:bigint"`
}

// BeforeCreate pre-create hook - generates UUID
func (e *Expense) BeforeCreate(tx *gorm.DB) error {
	if e.ID == "" {
		e.ID = uuid.New().String()
	}
	if e.UpdatedAt == 0 {
		e.UpdatedAt = time.Now().UnixMilli()
	}
	if e.Version == 0 {
		e.Version = 1
	}
	return nil
}

// TableName specifies the table name
func (Expense) TableName() string {
	return "expenses"
}

// ExpenseQuery expense query criteria
type ExpenseQuery struct {
	Keyword   string   `form:"keyword"`
	Type      string   `form:"type"`
	Month     string   `form:"month"`
	StartDate string   `form:"startDate"`
	EndDate   string   `form:"endDate"`
	MinAmount *float64 `form:"minAmount"`
	MaxAmount *float64 `form:"maxAmount"`
	Limit     int      `form:"limit,default=20"`
	Offset    int      `form:"offset,default=0"`
	Sort      string   `form:"sort,default=dateDesc"`
}

// ExpenseMeta metadata
type ExpenseMeta struct {
	UniqueTypes     []string `json:"uniqueTypes"`
	AvailableMonths []string `json:"availableMonths"`
}

// ExpenseStats expense statistics - fully compatible with JS version
type ExpenseStats struct {
	Count            int                             `json:"count" binding:"required"`
	TotalAmount      float64                         `json:"totalAmount" binding:"required"`
	AverageAmount    float64                         `json:"averageAmount" binding:"required"`
	MedianAmount     float64                         `json:"medianAmount" binding:"required"`
	MinAmount        float64                         `json:"minAmount" binding:"required"`
	MaxAmount        float64                         `json:"maxAmount" binding:"required"`
	TypeDistribution map[string]TypeDistributionItem `json:"typeDistribution" binding:"required"`
}

// TypeDistributionItem type distribution statistics item
type TypeDistributionItem struct {
	Count      int     `json:"count"`
	Amount     float64 `json:"amount"`
	Percentage int     `json:"percentage"`
}

// DateGroup expense records grouped by date - fully consistent with JS version getExpensesByDate
type DateGroup struct {
	Date        string    `json:"date"`
	Count       int       `json:"count"`
	TotalAmount float64   `json:"totalAmount"`
	Expenses    []Expense `json:"expenses"`
}

// Validate validates fields
func (e *Expense) Validate() error {
	if e.Type == "" {
		return errors.New("expense type cannot be empty")
	}
	if e.Amount <= 0 {
		return errors.New("expense amount must be greater than 0")
	}
	if e.Date == "" {
		return errors.New("expense date cannot be empty")
	}
	// Validate date format is yyyy-mm-dd
	_, err := time.Parse("2006-01-02", e.Date)
	if err != nil {
		return errors.New("expense date format error, expected yyyy-mm-dd format")
	}
	return nil
}

// ValidateQuery validates query parameters
func (q *ExpenseQuery) Validate() error {
	// Validate sort parameters
	validSorts := map[string]bool{
		"dateAsc":    true,
		"dateDesc":   true,
		"amountAsc":  true,
		"amountDesc": true,
	}
	if q.Sort != "" && !validSorts[q.Sort] {
		return fmt.Errorf("invalid sort parameter: %s", q.Sort)
	}

	// Validate pagination parameters
	if q.Limit < 1 {
		return errors.New("limit parameter cannot be less than 1")
	}
	if q.Offset < 0 {
		return errors.New("offset parameter cannot be negative")
	}

	// Validate date format
	if q.StartDate != "" {
		if _, err := time.Parse("2006-01-02", q.StartDate); err != nil {
			return errors.New("start date format error, expected yyyy-mm-dd format")
		}
	}
	if q.EndDate != "" {
		if _, err := time.Parse("2006-01-02", q.EndDate); err != nil {
			return errors.New("end date format error, expected yyyy-mm-dd format")
		}
	}

	// Validate date range
	if q.StartDate != "" && q.EndDate != "" {
		start, _ := time.Parse("2006-01-02", q.StartDate)
		end, _ := time.Parse("2006-01-02", q.EndDate)
		if start.After(end) {
			return errors.New("start date cannot be later than end date")
		}
	}

	// Validate amount range
	if q.MinAmount != nil && *q.MinAmount < 0 {
		return errors.New("minimum amount cannot be negative")
	}
	if q.MaxAmount != nil && *q.MaxAmount < 0 {
		return errors.New("maximum amount cannot be negative")
	}
	if q.MinAmount != nil && q.MaxAmount != nil && *q.MinAmount > *q.MaxAmount {
		return errors.New("minimum amount cannot be greater than maximum amount")
	}

	// Validate month format
	if q.Month != "" {
		if _, err := time.Parse("2006-01", q.Month); err != nil {
			return fmt.Errorf("month format error, expected format: YYYY-MM")
		}
	}

	return nil
}

// ToMonthRange converts month to date range
func (q *ExpenseQuery) ToMonthRange() (string, string, error) {
	if q.Month == "" {
		return "", "", errors.New("month cannot be empty")
	}

	parsed, err := time.Parse("2006-01", q.Month)
	if err != nil {
		return "", "", fmt.Errorf("failed to parse month: %w", err)
	}

	startDate := time.Date(parsed.Year(), parsed.Month(), 1, 0, 0, 0, 0, time.UTC)
	endDate := startDate.AddDate(0, 1, -1)

	return startDate.Format("2006-01-02"), endDate.Format("2006-01-02"), nil
}

// ApplyToQuery applies query criteria to GORM query
func (q *ExpenseQuery) ApplyToQuery(db *gorm.DB) *gorm.DB {
	// Default filter for non-deleted records
	db = db.Where("deletedAt IS NULL")
	if q.Keyword != "" {
		keyword := "%" + q.Keyword + "%"
		db = db.Where("(type LIKE ? OR remark LIKE ?)", keyword, keyword)
	}
	if q.Type != "" {
		db = db.Where("type = ?", q.Type)
	}
	if q.StartDate != "" && q.EndDate != "" {
		db = db.Where("date BETWEEN ? AND ?", q.StartDate, q.EndDate)
	} else if q.StartDate != "" {
		db = db.Where("date >= ?", q.StartDate)
	} else if q.EndDate != "" {
		db = db.Where("date <= ?", q.EndDate)
	}
	if q.MinAmount != nil {
		db = db.Where("amount >= ?", *q.MinAmount)
	}
	if q.MaxAmount != nil {
		db = db.Where("amount <= ?", *q.MaxAmount)
	}
	return db
}

// ApplySort applies sorting
func (q *ExpenseQuery) ApplySort(db *gorm.DB) *gorm.DB {
	switch q.Sort {
	case "dateAsc":
		return db.Order("date ASC")
	case "dateDesc":
		return db.Order("date DESC")
	case "amountAsc":
		return db.Order("amount ASC")
	case "amountDesc":
		return db.Order("amount DESC")
	default:
		return db.Order("date DESC")
	}
}

// GetStatsWithSQL gets statistics using raw SQL - fully compatible with JS version
func GetStatsWithSQL(db *gorm.DB, query *ExpenseQuery) (*ExpenseStats, error) {
	stats := &ExpenseStats{
		TypeDistribution: make(map[string]TypeDistributionItem),
	}

	// Build SQL query
	sql := db.Model(&Expense{})

	// Apply query criteria
	query.ApplyToQuery(sql)

	// Get total amount and count
	var totalAmount float64
	var count int64

	if err := sql.Select("COALESCE(SUM(amount), 0)").Row().Scan(&totalAmount); err != nil {
		return nil, fmt.Errorf("failed to get total amount: %w", err)
	}
	stats.TotalAmount = totalAmount

	if err := sql.Count(&count).Error; err != nil {
		return nil, fmt.Errorf("failed to get total count: %w", err)
	}
	stats.Count = int(count)

	if count > 0 {
		stats.AverageAmount = stats.TotalAmount / float64(count)
	}

	// Get all records for statistics (suitable for small datasets)
	var allExpenses []Expense
	if err := query.ApplyToQuery(db.Model(&Expense{})).Find(&allExpenses).Error; err != nil {
		return nil, fmt.Errorf("failed to get expense records: %w", err)
	}

	if len(allExpenses) == 0 {
		// Set default values for empty data
		stats.MedianAmount = 0
		stats.MinAmount = 0
		stats.MaxAmount = 0
		return stats, nil
	}

	// Calculate max and min values
	minAmount := allExpenses[0].Amount
	maxAmount := allExpenses[0].Amount

	// By type statistics
	typeMap := make(map[string][]float64)
	for _, expense := range allExpenses {
		// Update max and min values
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

	// Calculate median
	var amounts []float64
	for _, expense := range allExpenses {
		amounts = append(amounts, expense.Amount)
	}

	// Sort
	for i := 0; i < len(amounts); i++ {
		for j := i + 1; j < len(amounts); j++ {
			if amounts[i] > amounts[j] {
				amounts[i], amounts[j] = amounts[j], amounts[i]
			}
		}
	}

	// Calculate median
	if len(amounts) > 0 {
		if len(amounts)%2 == 0 {
			stats.MedianAmount = (amounts[len(amounts)/2-1] + amounts[len(amounts)/2]) / 2
		} else {
			stats.MedianAmount = amounts[len(amounts)/2]
		}
	}

	// Build type distribution statistics - fully consistent with JS version
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
			Count:      len(amounts),
			Amount:     typeTotal,
			Percentage: percentage,
		}
	}

	return stats, nil
}