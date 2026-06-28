package database

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	
	"homemoney/internal/models"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"github.com/glebarez/sqlite"
)

// Database database connection configuration
type Database struct {
	DB *gorm.DB
}

// InitDB initializes database connection
func InitDB(dbPath string) (*Database, error) {
	// Ensure database directory exists
	dbDir := filepath.Dir(dbPath)
	if err := os.MkdirAll(dbDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create database directory: %w", err)
	}

	// Connect to database
	db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
		// Disable soft delete to be compatible with JS version table structure
		DisableForeignKeyConstraintWhenMigrating: true,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to connect database: %w", err)
	}

	// Auto migrate database structure
	if err := AutoMigrate(db); err != nil {
		return nil, fmt.Errorf("failed to migrate database: %w", err)
	}

	log.Println("Database connected and migrated successfully")
	return &Database{DB: db}, nil
}

// AutoMigrate auto migrates database structure
func AutoMigrate(db *gorm.DB) error {
	// Execute migration
	err := db.AutoMigrate(
		&models.Expense{},
		&models.Member{},
		&models.ErrorReport{},
	)
	
	// If table already exists, manually add missing columns via ALTER TABLE
	if err != nil && strings.Contains(err.Error(), "already exists") {
		log.Println("Table already exists, checking for missing columns...")
		// Add missing columns (ignore errors for existing columns)
		alterStatements := []string{
			"ALTER TABLE expenses ADD COLUMN deletedAt bigint",
			"ALTER TABLE expenses ADD COLUMN updatedAt bigint NOT NULL DEFAULT 0",
			"ALTER TABLE expenses ADD COLUMN version integer NOT NULL DEFAULT 1",
		}
		for _, stmt := range alterStatements {
			if execErr := db.Exec(stmt).Error; execErr != nil {
				if !strings.Contains(execErr.Error(), "duplicate column") {
					log.Printf("ALTER TABLE warning: %v", execErr)
				}
			}
		}
		// Clean up previously incorrectly added snake_case columns
		cleanupStatements := []string{
			"ALTER TABLE expenses DROP COLUMN deleted_at",
			"ALTER TABLE expenses DROP COLUMN updated_at",
		}
		for _, stmt := range cleanupStatements {
			if execErr := db.Exec(stmt).Error; execErr != nil {
				// Ignore column not found errors
				if !strings.Contains(execErr.Error(), "no such column") {
					log.Printf("Cleanup warning: %v", execErr)
				}
			}
		}
		log.Println("Migration completed with existing tables")
		return nil
	}
	
	return err
}

// Close database connection
func (d *Database) Close() error {
	sqlDB, err := d.DB.DB()
	if err != nil {
		return err
	}
	return sqlDB.Close()
}

// Get database instance
func (d *Database) GetDB() *gorm.DB {
	return d.DB
}
