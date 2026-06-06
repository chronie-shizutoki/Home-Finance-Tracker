package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// SubscriptionPlan 订阅计划模型
type SubscriptionPlan struct {
	ID        string         `json:"id" gorm:"type:uuid;primaryKey"`
	Name      string         `json:"name" gorm:"type:varchar(255);not null"`
	Duration  int            `json:"duration" gorm:"type:integer;not null;comment:'订阅时长（天）'"`
	Price     float64        `json:"price" gorm:"type:float;not null"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName 指定表名
func (SubscriptionPlan) TableName() string {
	return "subscription_plans"
}

// BeforeCreate 创建前钩子
func (s *SubscriptionPlan) BeforeCreate(tx *gorm.DB) error {
	if s.ID == "" {
		s.ID = uuid.New().String()
	}
	if s.CreatedAt.IsZero() {
		s.CreatedAt = time.Now()
	}
	if s.UpdatedAt.IsZero() {
		s.UpdatedAt = time.Now()
	}
	return nil
}

// UserSubscription 用户订阅模型
type UserSubscription struct {
	ID         string         `json:"id" gorm:"type:uuid;primaryKey"`
	MemberID   string         `json:"memberId" gorm:"type:uuid;not null;index"`
	PlanID     string         `json:"planId" gorm:"type:uuid;not null"`
	StartDate  time.Time      `json:"startDate" gorm:"not null"`
	EndDate    time.Time      `json:"endDate" gorm:"not null"`
	IsActive   bool           `json:"isActive" gorm:"default:true"`
	CreatedAt  time.Time      `json:"createdAt"`
	UpdatedAt  time.Time      `json:"updatedAt"`
	DeletedAt  gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName 指定表名
func (UserSubscription) TableName() string {
	return "user_subscriptions"
}

// BeforeCreate 创建前钩子
func (u *UserSubscription) BeforeCreate(tx *gorm.DB) error {
	if u.ID == "" {
		u.ID = uuid.New().String()
	}
	if u.CreatedAt.IsZero() {
		u.CreatedAt = time.Now()
	}
	if u.UpdatedAt.IsZero() {
		u.UpdatedAt = time.Now()
	}
	return nil
}