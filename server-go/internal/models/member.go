package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Member 会员模型 - 与JS版本完全一致
type Member struct {
	ID        string         `json:"id" gorm:"type:uuid;default:gen_random_uuid();primaryKey"`
	Username  string         `json:"username" gorm:"type:varchar(255);not null;uniqueIndex;comment:'用户名'"`
	IsActive  bool           `json:"isActive" gorm:"default:false;comment:'是否激活'"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `json:"-" gorm:"index"`
}

// MemberResponse 会员响应结构 - 包含会员信息及订阅状态
type MemberResponse struct {
	ID                string           `json:"id"`
	Username          string           `json:"username"`
	IsActive          bool             `json:"isActive"`
	CreatedAt         *time.Time       `json:"createdAt,omitempty"`
	UpdatedAt         *time.Time       `json:"updatedAt,omitempty"`
	CurrentSubscription *UserSubscription `json:"currentSubscription,omitempty"`
}

// TableName 指定表名
func (Member) TableName() string {
	return "Members"
}

// BeforeCreate 创建前钩子 - 生成UUID
func (m *Member) BeforeCreate(tx *gorm.DB) error {
	if m.ID == "" {
		m.ID = uuid.New().String()
	}
	if m.CreatedAt.IsZero() {
		m.CreatedAt = time.Now()
	}
	if m.UpdatedAt.IsZero() {
		m.UpdatedAt = time.Now()
	}
	return nil
}

// BeforeUpdate 更新前钩子
func (m *Member) BeforeUpdate(tx *gorm.DB) error {
	m.UpdatedAt = time.Now()
	return nil
}

// ToJSON 转换为JSON格式 - 保持与JS版本一致
func (m *Member) ToJSON() map[string]interface{} {
	return map[string]interface{}{
		"id":        m.ID,
		"username":  m.Username,
		"isActive":  m.IsActive,
		"createdAt": m.CreatedAt.Format("2006-01-02 15:04:05"),
		"updatedAt": m.UpdatedAt.Format("2006-01-02 15:04:05"),
	}
}