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

// Database 数据库连接配置
type Database struct {
	DB *gorm.DB
}

// InitDB 初始化数据库连接
func InitDB(dbPath string) (*Database, error) {
	// 确保数据库目录存在
	dbDir := filepath.Dir(dbPath)
	if err := os.MkdirAll(dbDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create database directory: %w", err)
	}

	// 连接数据库
	db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
		// 禁用软删除功能，以兼容JS版本的表结构
		DisableForeignKeyConstraintWhenMigrating: true,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to connect database: %w", err)
	}

	// 自动迁移数据库结构
	if err := AutoMigrate(db); err != nil {
		return nil, fmt.Errorf("failed to migrate database: %w", err)
	}

	log.Println("Database connected and migrated successfully")
	return &Database{DB: db}, nil
}

// AutoMigrate 自动迁移数据库结构
func AutoMigrate(db *gorm.DB) error {
	// 执行迁移
	err := db.AutoMigrate(
		&models.Expense{},
		&models.Member{},
		&models.ErrorReport{},
	)
	
	// 如果表已存在，通过手动ALTER TABLE添加缺失的列
	if err != nil && strings.Contains(err.Error(), "already exists") {
		log.Println("Table already exists, checking for missing columns...")
		// 添加缺失的列（忽略已存在的列错误）
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
		// 清理之前错误添加的snake_case列
		cleanupStatements := []string{
			"ALTER TABLE expenses DROP COLUMN deleted_at",
			"ALTER TABLE expenses DROP COLUMN updated_at",
		}
		for _, stmt := range cleanupStatements {
			if execErr := db.Exec(stmt).Error; execErr != nil {
				// 忽略列不存在的错误
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

// Close 关闭数据库连接
func (d *Database) Close() error {
	sqlDB, err := d.DB.DB()
	if err != nil {
		return err
	}
	return sqlDB.Close()
}

// GetDB 获取数据库实例
func (d *Database) GetDB() *gorm.DB {
	return d.DB
}
