package service

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// LogService log service
type LogService struct {
	db *gorm.DB
}

// NewLogService creates a new log service instance
func NewLogService(db *gorm.DB) *LogService {
	return &LogService{db: db}
}

// LogData log data structure
type LogData struct {
	Timestamp string                 `json:"timestamp"`
	Type      string                 `json:"type"`
	Action    string                 `json:"action,omitempty"`
	RequestID string                 `json:"requestId,omitempty"`
	User      map[string]interface{} `json:"user,omitempty"`
	Device    map[string]interface{} `json:"device,omitempty"`
	Page      map[string]interface{} `json:"page,omitempty"`
	Details   map[string]interface{} `json:"details,omitempty"`
	// API related fields
	Request  map[string]interface{} `json:"request,omitempty"`
	Response map[string]interface{} `json:"response,omitempty"`
	Error    map[string]interface{} `json:"error,omitempty"`
	Duration int64                  `json:"duration,omitempty"`
	// Performance related fields
	Metric  string `json:"metric,omitempty"`
	Value   string `json:"value,omitempty"`
	Context string `json:"context,omitempty"`
	// Console log fields
	Level   string `json:"level,omitempty"`
	Message string `json:"message,omitempty"`
}

// QueryLogParams log query parameters
type QueryLogParams struct {
	Limit     int    `form:"limit,default=100"`
	Offset    int    `form:"offset,default=0"`
	Type      string `form:"type"`
	StartDate string `form:"startDate"`
	EndDate   string `form:"endDate"`
	Username  string `form:"username"`
}

// LogStats log statistics structure
type LogStats struct {
	Total     int                    `json:"total"`
	TypeStats []LogTypeStat          `json:"typeStats"`
	Period    map[string]interface{} `json:"period"`
}

// LogTypeStat log type statistics
type LogTypeStat struct {
	Type  string `json:"type"`
	Count int    `json:"count"`
}

// InitLogTable initializes the log table
func (s *LogService) InitLogTable() error {
	query := `
		CREATE TABLE IF NOT EXISTS operation_logs (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			timestamp TEXT NOT NULL,
			type TEXT NOT NULL,
			action TEXT,
			request_id TEXT,
			user_info TEXT,
			device_info TEXT,
			page_info TEXT,
			details TEXT,
			created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
		);
		
		-- Create indexes to improve query performance
		CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON operation_logs(timestamp);
	CREATE INDEX IF NOT EXISTS idx_logs_type ON operation_logs(type);
	`
	if err := s.db.Exec(query).Error; err != nil {
		return fmt.Errorf("failed to initialize log table: %w", err)
	}
	return nil
}

// SaveLog saves an operation log
func (s *LogService) SaveLog(logData LogData) error {
	// Validate required fields
	if logData.Timestamp == "" || logData.Type == "" {
		return errors.New("invalid parameters")
	}

	// Build details field
	detailsToSave, err := s.buildDetailsToSave(logData)
	if err != nil {
		return err
	}

	// Serialize JSON fields
	userInfo, _ := json.Marshal(logData.User)
	deviceInfo, _ := json.Marshal(logData.Device)
	pageInfo, _ := json.Marshal(logData.Page)

	// Use transaction to ensure data consistency
	tx := s.db.Begin()
	if tx.Error != nil {
		return fmt.Errorf("failed to begin transaction: %w", tx.Error)
	}
	defer tx.Rollback()

	query := `
		INSERT INTO operation_logs 
		(timestamp, type, action, request_id, user_info, device_info, page_info, details) 
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`

	err = tx.Exec(query,
		logData.Timestamp,
		logData.Type,
		logData.Action,
		logData.RequestID,
		string(userInfo),
		string(deviceInfo),
		string(pageInfo),
		detailsToSave,
	).Error
	if err != nil {
		return fmt.Errorf("failed to insert log: %w", err)
	}

	return tx.Commit().Error
}

// buildDetailsToSave builds the details field to save
func (s *LogService) buildDetailsToSave(logData LogData) (string, error) {
	detailsToSave := make(map[string]interface{})

	// Process different information based on log type
	switch logData.Type {
	case "api_request", "api_response", "api_error":
		// API related log processing
		if logData.Request != nil {
			detailsToSave["method"] = logData.Request["method"]
			detailsToSave["url"] = logData.Request["url"]
			detailsToSave["params"] = logData.Request["params"]
			detailsToSave["body"] = logData.Request["body"]
			detailsToSave["hasBody"] = logData.Request["hasBody"]
			detailsToSave["bodyType"] = logData.Request["bodyType"]
			detailsToSave["bodySize"] = logData.Request["bodySize"]
		}

		if logData.Response != nil {
			detailsToSave["status"] = logData.Response["status"]
			detailsToSave["statusText"] = logData.Response["statusText"]
			detailsToSave["responseData"] = logData.Response["data"]
			detailsToSave["responseHeaders"] = logData.Response["headers"]
			if data, ok := logData.Response["data"].(map[string]interface{}); ok {
				detailsToSave["truncated"] = data["truncated"]
			}
		}

		if logData.Error != nil {
			detailsToSave["error"] = logData.Error
		}

		if logData.Duration > 0 {
			detailsToSave["duration"] = logData.Duration
		}

	case "console_log":
		// Console log special handling
		detailsToSave["level"] = logData.Level
		detailsToSave["message"] = logData.Message

	case "performance":
		// Performance log processing
		detailsToSave["metric"] = logData.Metric
		detailsToSave["value"] = logData.Value
		detailsToSave["context"] = logData.Context

	default:
		// General handling for other log types
		if logData.Error != nil {
			detailsToSave["error"] = logData.Error
		}

		if logData.Details != nil {
			for k, v := range logData.Details {
				detailsToSave[k] = v
			}
		}
	}

	// Remove null values
	for k, v := range detailsToSave {
		if v == nil {
			delete(detailsToSave, k)
		}
	}

	// If details is empty, return null
	if len(detailsToSave) == 0 {
		return "null", nil
	}

	detailsJSON, err := json.Marshal(detailsToSave)
	if err != nil {
		return "", fmt.Errorf("failed to serialize details: %w", err)
	}

	return string(detailsJSON), nil
}

// GetLogs retrieves log list
func (s *LogService) GetLogs(params QueryLogParams) ([]map[string]interface{}, int, error) {
	// Build query SQL
	sql, args, err := s.buildLogQuerySQL(params)
	if err != nil {
		return nil, 0, err
	}

	// Execute query
	rows, err := s.db.Raw(sql, args...).Rows()
	if err != nil {
		return nil, 0, fmt.Errorf("failed to query logs: %w", err)
	}
	defer func() { _ = rows.Close() }()

	// Parse results
	logs, err := s.parseLogRows(rows)
	if err != nil {
		return nil, 0, err
	}

	// Get total count
	total, err := s.getLogsCount(params)
	if err != nil {
		return nil, 0, err
	}

	return logs, total, nil
}

// getLogsCount retrieves total log count
func (s *LogService) getLogsCount(params QueryLogParams) (int, error) {
	// Build count SQL
	countSQL := `SELECT COUNT(*) as count FROM operation_logs WHERE 1=1`
	var args []interface{}

	if params.Username != "" {
		countSQL += " AND (user_info LIKE ? OR user_info LIKE ?)"
		args = append(args, `%"username":"`+params.Username+`"%`, `%"email":"`+params.Username+`"%`)
	}

	if params.Type != "" {
		countSQL += " AND type = ?"
		args = append(args, params.Type)
	}

	if params.StartDate != "" {
		countSQL += " AND timestamp >= ?"
		args = append(args, params.StartDate)
	}

	if params.EndDate != "" {
		countSQL += " AND timestamp <= ?"
		args = append(args, params.EndDate)
	}

	var count int64
	err := s.db.Raw(countSQL, args...).Count(&count).Error
	if err != nil {
		return 0, fmt.Errorf("failed to get log count: %w", err)
	}
	return int(count), nil
}

// buildLogQuerySQL builds log query SQL
func (s *LogService) buildLogQuerySQL(params QueryLogParams) (string, []interface{}, error) {
	sql := `SELECT * FROM operation_logs WHERE 1=1`
	var args []interface{}

	if params.Type != "" {
		sql += " AND type = ?"
		args = append(args, params.Type)
	}

	if params.Username != "" {
		sql += " AND (user_info LIKE ? OR user_info LIKE ?)"
		args = append(args, `%"username":"`+params.Username+`"%`, `%"email":"`+params.Username+`"%`)
	}

	if params.StartDate != "" {
		sql += " AND timestamp >= ?"
		args = append(args, params.StartDate)
	}

	if params.EndDate != "" {
		sql += " AND timestamp <= ?"
		args = append(args, params.EndDate)
	}

	sql += " ORDER BY timestamp DESC LIMIT ? OFFSET ?"
	args = append(args, params.Limit, params.Offset)

	return sql, args, nil
}

// parseLogRows parses log query results
func (s *LogService) parseLogRows(rows *sql.Rows) ([]map[string]interface{}, error) {
	columns, err := rows.Columns()
	if err != nil {
		return nil, err
	}

	var logs []map[string]interface{}

	for rows.Next() {
		// Create column value receivers
		scanArgs := make([]interface{}, len(columns))
		values := make([]interface{}, len(columns))
		for i := range columns {
			scanArgs[i] = &values[i]
		}

		// Scan row
		if err := rows.Scan(scanArgs...); err != nil {
			return nil, err
		}

		// Build result map
		log := make(map[string]interface{})
		for i, col := range columns {
			val := values[i]

			// Handle JSON fields
			if val != nil && (col == "user_info" || col == "device_info" || col == "page_info" || col == "details") {
				var jsonData interface{}
				if err := json.Unmarshal(val.([]byte), &jsonData); err == nil {
					// Convert column names
					switch col {
					case "user_info":
						log["user"] = jsonData
					case "device_info":
						log["device"] = jsonData
					case "page_info":
						log["page"] = jsonData
					case "details":
						log["details"] = jsonData
					default:
						log[col] = jsonData
					}
				}
			} else {
				// Direct assignment for other fields
				log[col] = val
			}
		}

		logs = append(logs, log)
	}

	return logs, nil
}

// GetLogStats retrieves log statistics
func (s *LogService) GetLogStats(params QueryLogParams) (LogStats, error) {
	// Log type list
	logTypes := []string{"user_action", "api_request", "api_response", "api_error", "page_error", "performance", "console_log"}

	// Get count for each type
	var typeStats []LogTypeStat
	for _, logType := range logTypes {
		typeParams := params
		typeParams.Type = logType
		count, err := s.getLogsCount(typeParams)
		if err != nil {
			return LogStats{}, err
		}
		typeStats = append(typeStats, LogTypeStat{Type: logType, Count: count})
	}

	// Get total count
	total, err := s.getLogsCount(params)
	if err != nil {
		return LogStats{}, err
	}

	// Build statistics result
	stats := LogStats{
		Total:     total,
		TypeStats: typeStats,
		Period: map[string]interface{}{
			"start": params.StartDate,
			"end":   params.EndDate,
		},
	}

	return stats, nil
}

// CleanLogs cleans expired logs
func (s *LogService) CleanLogs(daysToKeep int) (int64, error) {
	if daysToKeep < 1 {
		return 0, errors.New("invalid log cleanup parameters")
	}

	// Calculate cleanup date
	cleanDate := time.Now().AddDate(0, 0, -daysToKeep)
	cleanDateStr := cleanDate.Format(time.RFC3339)

	// Execute deletion
	result := s.db.Exec("DELETE FROM operation_logs WHERE timestamp < ?", cleanDateStr)
	err := result.Error
	if err != nil {
		return 0, fmt.Errorf("failed to clean logs: %w", err)
	}

	// Get deleted row count
	deletedCount := result.RowsAffected
	return deletedCount, nil
}

// HandleLog handles log requests (for async processing)
func (s *LogService) HandleLog(c *gin.Context, logData LogData) {
	// Process asynchronously in goroutine, do not block response
	go func() {
		if err := s.SaveLog(logData); err != nil {
			fmt.Printf("async log save failed: %v\n", err)
			// Do not return error to avoid affecting main flow
		}
	}()
}
