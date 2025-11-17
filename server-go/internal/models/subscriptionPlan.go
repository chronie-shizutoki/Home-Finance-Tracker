package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// SubscriptionPlan 订阅计划模型 - 与JS版本完全一致
type SubscriptionPlan struct {
	ID          string         `json:"id" gorm:"type:uuid;default:gen_random_uuid();primaryKey"`
	Name        string         `json:"name" gorm:"type:varchar(255);not null;uniqueIndex;comment:'计划名称'"`
	Description string         `json:"description" gorm:"type:text;comment:'计划描述'"`
	Duration    int           `json:"duration" gorm:"not null;comment:'订阅时长（天数）'"`
	Price       float64       `json:"price" gorm:"type:decimal(10,2);not null;comment:'价格'"`
	Period      string        `json:"period" gorm:"type:varchar(50);not null;comment:'订阅周期类型'"`
	IsActive    bool          `json:"isActive" gorm:"default:true;comment:'是否激活'"`
	CreatedAt   time.Time     `json:"createdAt"`
	UpdatedAt   time.Time     `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName 指定表名
func (SubscriptionPlan) TableName() string {
	return "SubscriptionPlans"
}

// BeforeCreate 创建前钩子
func (sp *SubscriptionPlan) BeforeCreate(tx *gorm.DB) error {
	if sp.ID == "" {
		sp.ID = uuid.New().String()
	}
	if sp.CreatedAt.IsZero() {
		sp.CreatedAt = time.Now()
	}
	if sp.UpdatedAt.IsZero() {
		sp.UpdatedAt = time.Now()
	}
	return nil
}

// BeforeUpdate 更新前钩子
func (sp *SubscriptionPlan) BeforeUpdate(tx *gorm.DB) error {
	sp.UpdatedAt = time.Now()
	return nil
}

// ToJSON 转换为JSON格式
func (sp *SubscriptionPlan) ToJSON() map[string]interface{} {
	return map[string]interface{}{
		"id":          sp.ID,
		"name":        sp.Name,
		"description": sp.Description,
		"duration":    sp.Duration,
		"price":       sp.Price,
		"period":      sp.Period,
		"isActive":    sp.IsActive,
		"createdAt":   sp.CreatedAt.Format("2006-01-02 15:04:05"),
		"updatedAt":   sp.UpdatedAt.Format("2006-01-02 15:04:05"),
	}
}