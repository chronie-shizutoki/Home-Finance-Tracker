package models

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// JSONMap custom JSON type for storing and reading JSON data - compatible with JS version Sequelize JSON type
type JSONMap map[string]interface{}

// Scan implements sql.Scanner interface
func (j *JSONMap) Scan(value interface{}) error {
	if value == nil {
		*j = nil
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("type assertion failed, expected []byte")
	}
	return json.Unmarshal(bytes, j)
}

// Value implements driver.Valuer interface
func (j JSONMap) Value() (driver.Value, error) {
	if j == nil {
		return nil, nil
	}
	return json.Marshal(j)
}

// ErrorReport model - fully consistent with JS version
type ErrorReport struct {
	ID          string         `json:"id" gorm:"type:uuid;primaryKey"`
	MemberID    *string        `json:"memberId,omitempty" gorm:"type:uuid;index;comment:'Member ID'"`
	ErrorType   string         `json:"errorType" gorm:"type:varchar(255);not null;index;comment:'Error type'"`
	Message     string         `json:"message" gorm:"type:text;not null;comment:'Error message'"`
	StackTrace  *string        `json:"stackTrace,omitempty" gorm:"type:text;comment:'Stack trace'"`
	DeviceInfo  JSONMap        `json:"deviceInfo,omitempty" gorm:"type:text;comment:'Device info JSON'"`
	AppVersion  *string        `json:"appVersion,omitempty" gorm:"type:varchar(50);comment:'App version'"`
	AppBuild    *string        `json:"appBuild,omitempty" gorm:"type:varchar(50);comment:'Build version'"`
	Environment *string        `json:"environment,omitempty" gorm:"type:varchar(50);comment:'Environment'"`
	IsProcessed bool           `json:"isProcessed" gorm:"default:false;index;comment:'Is processed'"`
	ProcessedAt *time.Time     `json:"processedAt,omitempty" gorm:"comment:'Processed time'"`
	CreatedAt   time.Time      `json:"createdAt"`
	UpdatedAt   time.Time      `json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName specifies the table name
func (ErrorReport) TableName() string {
	return "error_reports"
}

// BeforeCreate pre-create hook
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

// BeforeUpdate pre-update hook
func (e *ErrorReport) BeforeUpdate(tx *gorm.DB) error {
	e.UpdatedAt = time.Now()
	return nil
}