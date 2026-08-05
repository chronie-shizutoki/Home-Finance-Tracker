package repository

import (
	"fmt"
	"time"

	"homemoney/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// ExpenseRepository expense record data repository
type ExpenseRepository struct {
	db *gorm.DB
}

// NewExpenseRepository creates a new expense repository
func NewExpenseRepository(db *gorm.DB) *ExpenseRepository {
	return &ExpenseRepository{
		db: db,
	}
}

// Create creates an expense record
func (r *ExpenseRepository) Create(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return r.db.Create(expense).Error
}

// FindByID finds an expense record by ID
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

// FindWithPagination finds expense records with pagination
func (r *ExpenseRepository) FindWithPagination(query *models.ExpenseQuery) ([]models.Expense, int64, error) {
	var expenses []models.Expense
	var total int64

	// Validate query parameters
	if err := query.Validate(); err != nil {
		return nil, 0, fmt.Errorf("query parameter validation failed: %w", err)
	}

	// Build base query
	baseQuery := r.db.Model(&models.Expense{})

	// Apply query conditions
	query.ApplyToQuery(baseQuery)

	// Calculate total count
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	// Apply sort and pagination
	query.ApplySort(baseQuery)
	baseQuery = baseQuery.Offset(query.Offset).Limit(query.Limit)

	// Execute query
	if err := baseQuery.Find(&expenses).Error; err != nil {
		return nil, 0, err
	}

	return expenses, total, nil
}

// Delete soft-deletes an expense record (sets deletedAt)
func (r *ExpenseRepository) Delete(id string) error {
	now := time.Now().UnixMilli()
	result := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Update("deletedAt", now)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("record not found")
	}
	return nil
}

// HardDelete hard-deletes an expense record
func (r *ExpenseRepository) HardDelete(id string) error {
	result := r.db.Where("id = ?", id).Delete(&models.Expense{})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("record not found")
	}
	return nil
}

// GetStatistics retrieves statistics data
func (r *ExpenseRepository) GetStatistics(query *models.ExpenseQuery) (*models.ExpenseStats, error) {
	if query != nil {
		if err := query.Validate(); err != nil {
			return nil, fmt.Errorf("query parameter validation failed: %w", err)
		}
	}
	return models.GetStatsWithSQL(r.db, query)
}

// GetMeta retrieves metadata
func (r *ExpenseRepository) GetMeta() (*models.ExpenseMeta, error) {
	var meta models.ExpenseMeta

	// Get unique types
	var uniqueTypes []string
	if err := r.db.Model(&models.Expense{}).Distinct().Pluck("type", &uniqueTypes).Error; err != nil {
		return nil, err
	}
	meta.UniqueTypes = uniqueTypes

	// Get available months - since date is now string type, use substring to get year-month
	var availableMonths []string
	if err := r.db.Model(&models.Expense{}).
		Order("date DESC").
		Distinct().
		Pluck("SUBSTRING(date, 1, 7)", &availableMonths).Error; err != nil {
		return nil, fmt.Errorf("failed to get month data: %w", err)
	}
	meta.AvailableMonths = availableMonths

	return &meta, nil
}

// Exists checks if a record exists (not deleted)
func (r *ExpenseRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Expense{}).Where("id = ? AND deletedAt IS NULL", id).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

// BatchCreate batch creates expense records
func (r *ExpenseRepository) BatchCreate(expenses []models.Expense) error {
	if len(expenses) == 0 {
		return nil
	}

	// Validate all records
	for i, expense := range expenses {
		if err := expense.Validate(); err != nil {
			return fmt.Errorf("record %d validation failed: %w", i+1, err)
		}
	}

	// Process in batches, 50 records per batch
	batchSize := 50
	for i := 0; i < len(expenses); i += batchSize {
		end := i + batchSize
		if end > len(expenses) {
			end = len(expenses)
		}

		batch := expenses[i:end]
		if err := r.db.CreateInBatches(batch, batchSize).Error; err != nil {
			return fmt.Errorf("batch %d creation failed: %w", i/batchSize+1, err)
		}
	}
	return nil
}

// FindByDates finds records by date list - used for import deduplication
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

// Update updates an expense record
func (r *ExpenseRepository) Update(expense *models.Expense) error {
	if err := expense.Validate(); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return r.db.Save(expense).Error
}

// SyncExpenses syncs expense records - fully consistent with JS version
func (r *ExpenseRepository) SyncExpenses(lastSyncTime *int64, changes []models.Expense, localIDs []string) ([]models.Expense, []gin.H, error) {
	serverChanges := make([]models.Expense, 0)
	conflicts := make([]gin.H, 0)

	// If client provided localIDs, return records missing from client
	if len(localIDs) > 0 {
		// Get all server record IDs (active records only; soft-deleted
		// tombstones must not be re-pushed to clients, otherwise a delete
		// that was already synced would be resurrected on the device).
		var allServerRecords []models.Expense
		if err := r.db.Model(&models.Expense{}).Where("deletedAt IS NULL").Select("id").Find(&allServerRecords).Error; err != nil {
			return nil, nil, fmt.Errorf("failed to get server records: %w", err)
		}

		allServerIDs := make(map[string]bool)
		for _, r := range allServerRecords {
			allServerIDs[r.ID] = true
		}
		localIDSet := make(map[string]bool)
		for _, id := range localIDs {
			localIDSet[id] = true
		}

		// Find records that exist on server but not on client
		var missingIDs []string
		for id := range allServerIDs {
			if !localIDSet[id] {
				missingIDs = append(missingIDs, id)
			}
		}

		if len(missingIDs) > 0 {
			// Only return active records; exclude soft-deleted tombstones.
			if err := r.db.Where("id IN ?", missingIDs).Where("deletedAt IS NULL").Find(&serverChanges).Error; err != nil {
				return nil, nil, fmt.Errorf("failed to get missing records: %w", err)
			}
		}
	} else if lastSyncTime != nil && *lastSyncTime > 0 {
		// Old behavior: return records updated after lastSyncTime
		if err := r.db.Where("updatedAt > ?", *lastSyncTime).Where("deletedAt IS NULL").Order("updatedAt ASC").Find(&serverChanges).Error; err != nil {
			return nil, nil, fmt.Errorf("failed to get updated records: %w", err)
		}
	}

	// Process changes submitted by client
	for _, change := range changes {
		if change.DeletedAt != nil {
			// Delete operation
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
			// Create new record
			if createErr := r.Create(&change); createErr != nil {
				fmt.Printf("Error processing change: %s, %v\n", change.ID, createErr)
			}
		} else if err != nil {
			fmt.Printf("Error processing change: %s, %v\n", change.ID, err)
		} else {
			if change.UpdatedAt > serverRecord.UpdatedAt {
				// Client version is newer, update server
				r.db.Model(&serverRecord).Updates(map[string]interface{}{
					"type":      change.Type,
					"remark":    change.Remark,
					"amount":    change.Amount,
					"date":      change.Date,
					"version":   change.Version,
					"updatedAt": change.UpdatedAt,
				})
			} else if change.UpdatedAt < serverRecord.UpdatedAt {
				// Conflict
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

// GetExpensesByDate groups expenses by date - fully consistent with JS version getExpensesByDate
// Returns format: [{date, count, totalAmount, expenses}, ...]
// Implements smart pagination: ensures records from the same date group are not split across pages
func (r *ExpenseRepository) GetExpensesByDate(query *models.ExpenseQuery) ([]models.DateGroup, int64, *models.ExpenseMeta, error) {
	var allExpenses []models.Expense
	var total int64

	baseQuery := r.db.Model(&models.Expense{})
	query.ApplyToQuery(baseQuery)

	// Calculate total count
	if err := baseQuery.Count(&total).Error; err != nil {
		return nil, 0, nil, err
	}

	// Apply sort and get all matching data
	query.ApplySort(baseQuery)
	if err := baseQuery.Find(&allExpenses).Error; err != nil {
		return nil, 0, nil, err
	}

	// Group by date
	groupedMap := make(map[string][]models.Expense)
	var dateOrder []string
	for _, e := range allExpenses {
		if _, exists := groupedMap[e.Date]; !exists {
			dateOrder = append(dateOrder, e.Date)
		}
		groupedMap[e.Date] = append(groupedMap[e.Date], e)
	}

	// Add statistics for each date group, consistent with JS version format
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

	// Sort date groups by sort type - consistent with JS version
	isAmountSort := query.Sort == "amountAsc" || query.Sort == "amountDesc"
	if isAmountSort {
		// When sorting by amount, date groups are sorted by the first record's amount in the group
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
		// Sort by date
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

	// Smart pagination: ensures records from the same date group are not split across pages
	// Paginate by date groups
	pageSize := query.Limit
	if pageSize < 1 {
		pageSize = 10
	}
	var pages [][]models.DateGroup
	var currentPageData []models.DateGroup
	var currentRecordCount int

	for _, group := range dateGroups {
		groupRecordCount := group.Count
		// If current page is empty, or adding this group won't exceed record limit too much, add to current page
		if currentRecordCount == 0 || currentRecordCount+groupRecordCount <= int(float64(pageSize)*1.5) {
			currentPageData = append(currentPageData, group)
			currentRecordCount += groupRecordCount
		} else {
			// Start a new page
			pages = append(pages, currentPageData)
			currentPageData = []models.DateGroup{group}
			currentRecordCount = groupRecordCount
		}
	}
	// Add the last page
	if len(currentPageData) > 0 {
		pages = append(pages, currentPageData)
	}

	// Get the requested page data
	pageNum := 1
	if query.Limit > 0 {
		pageNum = query.Offset/query.Limit + 1
	}
	var pagedData []models.DateGroup
	if pageNum > 0 && pageNum <= len(pages) {
		pagedData = pages[pageNum-1]
	}

	// Get metadata
	meta, err := r.GetMeta()
	if err != nil {
		meta = &models.ExpenseMeta{}
	}

	return pagedData, total, meta, nil
}

// FindAll retrieves all expense records (used for migration testing)
func (r *ExpenseRepository) FindAll() ([]models.Expense, error) {
	var expenses []models.Expense
	if err := r.db.Where("deletedAt IS NULL").Order("date DESC").Find(&expenses).Error; err != nil {
		return nil, err
	}
	return expenses, nil
}
