package database

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	
	"homemoney/internal/models"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
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
	// 执行迁移，忽略表已存在的错误
	err := db.AutoMigrate(
		&models.Member{},
		&models.SubscriptionPlan{},
		&models.UserSubscription{},
	)
	
	// 如果是表已存在的错误，记录日志并返回nil
	if err != nil && strings.Contains(err.Error(), "already exists") {
		log.Println("expenses表已存在，跳过迁移")
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
