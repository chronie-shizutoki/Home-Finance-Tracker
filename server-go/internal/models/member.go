package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Member model - fully consistent with JS version
// Note: createdAt/updatedAt use string type because JS version (Sequelize) stores as DATETIME string in SQLite
type Member struct {
	ID        string  `json:"id" gorm:"type:varchar(36);primaryKey"`
	Username  string  `json:"username" gorm:"type:varchar(255);not null;uniqueIndex;comment:'Username'"`
	Avatar    *string `json:"avatar,omitempty" gorm:"type:text;comment:'Avatar data'"`
	CreatedAt string  `json:"createdAt" gorm:"column:createdAt;type:text"`
	UpdatedAt string  `json:"updatedAt" gorm:"column:updatedAt;type:text"`
}

// TableName specifies the table name
func (Member) TableName() string {
	return "Members"
}

// BeforeCreate pre-create hook - generates UUID and timestamps
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

// BeforeUpdate pre-update hook
func (m *Member) BeforeUpdate(tx *gorm.DB) error {
	m.UpdatedAt = time.Now().Format("2006-01-02 15:04:05")
	return nil
}

// ToJSON converts to JSON format - consistent with JS version
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