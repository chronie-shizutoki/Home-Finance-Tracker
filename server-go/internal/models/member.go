package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Member 会员模型 - 与JS版本完全一致
// 注意: createdAt/updatedAt 使用 string 类型，因为JS版本(Sequelize)在SQLite中以DATETIME字符串存储
type Member struct {
	ID        string  `json:"id" gorm:"type:varchar(36);primaryKey"`
	Username  string  `json:"username" gorm:"type:varchar(255);not null;uniqueIndex;comment:'用户名'"`
	Avatar    *string `json:"avatar,omitempty" gorm:"type:text;comment:'头像数据'"`
	CreatedAt string  `json:"createdAt" gorm:"column:createdAt;type:text"`
	UpdatedAt string  `json:"updatedAt" gorm:"column:updatedAt;type:text"`
}

// TableName 指定表名
func (Member) TableName() string {
	return "Members"
}

// BeforeCreate 创建前钩子 - 生成UUID和时间戳
func (m *Member) BeforeCreate(tx *gorm.DB) error {
	if m.ID == "" {
		m.ID = uuid.New().String()
	}
	now := time.Now().Format("2006-01-02 15:04:05")
	if m.CreatedAt == "" {
		m.CreatedAt = now
	}
	if m.UpdatedAt == "" {
		m.UpdatedAt = now
	}
	return nil
}

// BeforeUpdate 更新前钩子
func (m *Member) BeforeUpdate(tx *gorm.DB) error {
	m.UpdatedAt = time.Now().Format("2006-01-02 15:04:05")
	return nil
}

// ToJSON 转换为JSON格式 - 保持与JS版本一致
func (m *Member) ToJSON() map[string]interface{} {
	result := map[string]interface{}{
		"id":        m.ID,
		"username":  m.Username,
		"createdAt": m.CreatedAt,
		"updatedAt": m.UpdatedAt,
	}
	if m.Avatar != nil {
		result["avatar"] = *m.Avatar
	}
	return result
}