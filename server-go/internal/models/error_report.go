package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// ErrorReport 错误报告模型 - 与JS版本完全一致
type ErrorReport struct {
	ID          string         `json:"id" gorm:"type:uuid;primaryKey"`
	MemberID    *string        `json:"memberId,omitempty" gorm:"type:uuid;index;comment:'会员ID'"`
	ErrorType   string         `json:"errorType" gorm:"type:varchar(255);not null;index;comment:'错误类型'"`
	Message     string         `json:"message" gorm:"type:text;not null;comment:'错误消息'"`
	StackTrace  *string        `json:"stackTrace,omitempty" gorm:"type:text;comment:'堆栈跟踪'"`
	DeviceInfo  *string        `json:"deviceInfo,omitempty" gorm:"type:text;comment:'设备信息JSON'"`
	AppVersion  *string        `json:"appVersion,omitempty" gorm:"type:varchar(50);comment:'应用版本'"`
	AppBuild    *string        `json:"appBuild,omitempty" gorm:"type:varchar(50);comment:'构建版本'"`
	Environment *string        `json:"environment,omitempty" gorm:"type:varchar(50);comment:'环境'"`
	IsProcessed bool           `json:"isProcessed" gorm:"default:false;index;comment:'是否已处理'"`
	ProcessedAt *time.Time     `json:"processedAt,omitempty" gorm:"comment:'处理时间'"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName 指定表名
func (ErrorReport) TableName() string {
	return "error_reports"
}

// BeforeCreate 创建前钩子
func (e *ErrorReport) BeforeCreate(tx *gorm.DB) error {
	if e.ID == "" {
		e.ID = uuid.New().String()
	}
	if e.CreatedAt.IsZero() {
		e.CreatedAt = time.Now()
	}
	if e.UpdatedAt.IsZero() {
		e.UpdatedAt = time.Now()
	}
	return nil
}

// BeforeUpdate 更新前钩子
func (e *ErrorReport) BeforeUpdate(tx *gorm.DB) error {
	e.UpdatedAt = time.Now()
	return nil
}