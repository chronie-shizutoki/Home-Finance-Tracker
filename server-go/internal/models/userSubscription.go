package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// UserSubscription 用户订阅模型 - 与JS版本完全一致
type UserSubscription struct {
	ID         string         `json:"id" gorm:"type:uuid;default:gen_random_uuid();primaryKey"`
	MemberID   string         `json:"memberId" gorm:"type:uuid;index;comment:'会员ID'"`
	PlanID     string         `json:"planId" gorm:"type:uuid;index;comment:'计划ID'"`
	StartDate  time.Time      `json:"startDate" gorm:"comment:'订阅开始日期'"`
	EndDate    time.Time      `json:"endDate" gorm:"comment:'订阅结束日期'"`
	Status     string        `json:"status" gorm:"type:varchar(50);default:'active';comment:'订阅状态'"`
	PaymentID  string        `json:"paymentId" gorm:"type:varchar(255);comment:'支付平台的交易ID'"`
	AutoRenew  bool          `json:"autoRenew" gorm:"default:false;comment:'是否自动续费'"`
	CreatedAt  time.Time     `json:"createdAt"`
	UpdatedAt  time.Time     `json:"updatedAt"`
	DeletedAt  gorm.DeletedAt `json:"-" gorm:"index"`

	// 关联关系
	Member Member           `json:"member,omitempty" gorm:"foreignKey:MemberID"`
	Plan   SubscriptionPlan `json:"plan,omitempty" gorm:"foreignKey:PlanID"`
}

// TableName 指定表名
func (UserSubscription) TableName() string {
	return "UserSubscriptions"
}

// BeforeCreate 创建前钩子
func (us *UserSubscription) BeforeCreate(tx *gorm.DB) error {
	if us.ID == "" {
		us.ID = uuid.New().String()
	}
	if us.CreatedAt.IsZero() {
		us.CreatedAt = time.Now()
	}
	if us.UpdatedAt.IsZero() {
		us.UpdatedAt = time.Now()
	}
	if us.Status == "" {
		us.Status = "active"
	}
	return nil
}

// BeforeUpdate 更新前钩子
func (us *UserSubscription) BeforeUpdate(tx *gorm.DB) error {
	us.UpdatedAt = time.Now()
	return nil
}

// IsActiveSubscription 检查订阅是否处于活跃状态
func (us *UserSubscription) IsActiveSubscription() bool {
	if us.Status != "active" {
		return false
	}
	now := time.Now()
	return now.After(us.StartDate) && now.Before(us.EndDate)
}

// ToJSON 转换为JSON格式
func (us *UserSubscription) ToJSON() map[string]interface{} {
	return map[string]interface{}{
		"id":         us.ID,
		"memberId":   us.MemberID,
		"planId":     us.PlanID,
		"startDate":  us.StartDate.Format("2006-01-02 15:04:05"),
		"endDate":    us.EndDate.Format("2006-01-02 15:04:05"),
		"status":     us.Status,
		"paymentId":  us.PaymentID,
		"autoRenew":  us.AutoRenew,
		"createdAt":  us.CreatedAt.Format("2006-01-02 15:04:05"),
		"updatedAt":  us.UpdatedAt.Format("2006-01-02 15:04:05"),
		"isActive":   us.IsActiveSubscription(),
	}
}