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

// LogService 日志服务
type LogService struct {
	db *gorm.DB
}

// NewLogService 创建日志服务实例
func NewLogService(db *gorm.DB) *LogService {
	return &LogService{db: db}
}

// LogData 日志数据结构
type LogData struct {
	Timestamp string                 `json:"timestamp"`
	Type      string                 `json:"type"`
	Action    string                 `json:"action,omitempty"`
	RequestID string                 `json:"requestId,omitempty"`
	User      map[string]interface{} `json:"user,omitempty"`
	Device    map[string]interface{} `json:"device,omitempty"`
	Page      map[string]interface{} `json:"page,omitempty"`
	Details   map[string]interface{} `json:"details,omitempty"`
	// API相关字段
	Request  map[string]interface{} `json:"request,omitempty"`
	Response map[string]interface{} `json:"response,omitempty"`
	Error    map[string]interface{} `json:"error,omitempty"`
	Duration int64                  `json:"duration,omitempty"`
	// 性能相关字段
	Metric  string `json:"metric,omitempty"`
	Value   string `json:"value,omitempty"`
	Context string `json:"context,omitempty"`
	// 控制台日志字段
	Level   string `json:"level,omitempty"`
	Message string `json:"message,omitempty"`
}

// QueryLogParams 日志查询参数
type QueryLogParams struct {
	Limit     int    `form:"limit,default=100"`
	Offset    int    `form:"offset,default=0"`
	Type      string `form:"type"`
	StartDate string `form:"startDate"`
	EndDate   string `form:"endDate"`
	Username  string `form:"username"`
}

// LogStats 日志统计结构
type LogStats struct {
	Total     int                    `json:"total"`
	TypeStats []LogTypeStat          `json:"typeStats"`
	Period    map[string]interface{} `json:"period"`
}

// LogTypeStat 日志类型统计
type LogTypeStat struct {
	Type  string `json:"type"`
	Count int    `json:"count"`
}

// InitLogTable 初始化日志表
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
		
		-- 创建索引以提高查询性能
		CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON operation_logs(timestamp);
	CREATE INDEX IF NOT EXISTS idx_logs_type ON operation_logs(type);
	`
	var err error
	err = s.db.Exec(query).Error
	if err != nil {
		return fmt.Errorf("初始化日志表失败: %w", err)
	}
	return nil
}

// SaveLog 保存操作日志
func (s *LogService) SaveLog(logData LogData) error {
	// 验证必要字段
	if logData.Timestamp == "" || logData.Type == "" {
		return errors.New("参数错误")
	}

	// 构建details字段
	detailsToSave, err := s.buildDetailsToSave(logData)
	if err != nil {
		return err
	}

	// 序列化JSON字段
	userInfo, _ := json.Marshal(logData.User)
	deviceInfo, _ := json.Marshal(logData.Device)
	pageInfo, _ := json.Marshal(logData.Page)

	// 使用事务确保数据一致性
	tx := s.db.Begin()
	if tx.Error != nil {
		return fmt.Errorf("开始事务失败: %w", tx.Error)
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
		return fmt.Errorf("插入日志失败: %w", err)
	}

	return tx.Commit().Error
}

// buildDetailsToSave 构建要保存的details字段
func (s *LogService) buildDetailsToSave(logData LogData) (string, error) {
	detailsToSave := make(map[string]interface{})

	// 根据日志类型处理不同的信息
	switch logData.Type {
	case "api_request", "api_response", "api_error":
		// API相关日志处理
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
		// 控制台日志特殊处理
		detailsToSave["level"] = logData.Level
		detailsToSave["message"] = logData.Message

	case "performance":
		// 性能日志处理
		detailsToSave["metric"] = logData.Metric
		detailsToSave["value"] = logData.Value
		detailsToSave["context"] = logData.Context

	default:
		// 其他类型日志的通用处理
		if logData.Error != nil {
			detailsToSave["error"] = logData.Error
		}

		if logData.Details != nil {
			for k, v := range logData.Details {
				detailsToSave[k] = v
			}
		}
	}

	// 移除空值
	for k, v := range detailsToSave {
		if v == nil {
			delete(detailsToSave, k)
		}
	}

	// 如果details为空，返回null
	if len(detailsToSave) == 0 {
		return "null", nil
	}

	detailsJSON, err := json.Marshal(detailsToSave)
	if err != nil {
		return "", fmt.Errorf("序列化details失败: %w", err)
	}

	return string(detailsJSON), nil
}

// GetLogs 获取日志列表
func (s *LogService) GetLogs(params QueryLogParams) ([]map[string]interface{}, int, error) {
	// 构建查询SQL
	sql, args, err := s.buildLogQuerySQL(params)
	if err != nil {
		return nil, 0, err
	}

	// 执行查询
	rows, err := s.db.Raw(sql, args...).Rows()
	if err != nil {
		return nil, 0, fmt.Errorf("查询日志失败: %w", err)
	}
	defer rows.Close()

	// 解析结果
	logs, err := s.parseLogRows(rows)
	if err != nil {
		return nil, 0, err
	}

	// 获取总数
	total, err := s.getLogsCount(params)
	if err != nil {
		return nil, 0, err
	}

	return logs, total, nil
}

// GetLogsCount 获取日志总数
func (s *LogService) getLogsCount(params QueryLogParams) (int, error) {
	// 构建计数SQL
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
		return 0, fmt.Errorf("获取日志数量失败: %w", err)
	}
	return int(count), nil
}

// buildLogQuerySQL 构建日志查询SQL
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

// parseLogRows 解析日志查询结果
func (s *LogService) parseLogRows(rows *sql.Rows) ([]map[string]interface{}, error) {
	columns, err := rows.Columns()
	if err != nil {
		return nil, err
	}

	var logs []map[string]interface{}

	for rows.Next() {
		// 创建列值接收器
		scanArgs := make([]interface{}, len(columns))
		values := make([]interface{}, len(columns))
		for i := range columns {
			scanArgs[i] = &values[i]
		}

		// 扫描行
		if err := rows.Scan(scanArgs...); err != nil {
			return nil, err
		}

		// 构建结果映射
		log := make(map[string]interface{})
		for i, col := range columns {
			val := values[i]

			// 处理JSON字段
			if val != nil && (col == "user_info" || col == "device_info" || col == "page_info" || col == "details") {
				var jsonData interface{}
				if err := json.Unmarshal(val.([]byte), &jsonData); err == nil {
					// 转换列名
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
				// 其他字段直接赋值
				log[col] = val
			}
		}

		logs = append(logs, log)
	}

	return logs, nil
}

// GetLogStats 获取日志统计信息
func (s *LogService) GetLogStats(params QueryLogParams) (LogStats, error) {
	// 日志类型列表
	logTypes := []string{"user_action", "api_request", "api_response", "api_error", "page_error", "performance", "console_log"}

	// 获取每种类型的数量
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

	// 获取总数量
	total, err := s.getLogsCount(params)
	if err != nil {
		return LogStats{}, err
	}

	// 构建统计结果
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

// CleanLogs 清理过期日志
func (s *LogService) CleanLogs(daysToKeep int) (int64, error) {
	if daysToKeep < 1 {
		return 0, errors.New("清理日志参数错误")
	}

	// 计算清理日期
	cleanDate := time.Now().AddDate(0, 0, -daysToKeep)
	cleanDateStr := cleanDate.Format(time.RFC3339)

	// 执行删除
	result := s.db.Exec("DELETE FROM operation_logs WHERE timestamp < ?", cleanDateStr)
	err := result.Error
	if err != nil {
		return 0, fmt.Errorf("清理日志失败: %w", err)
	}

	// 获取删除的行数
	deletedCount := result.RowsAffected
	return deletedCount, nil
}

// HandleLog 处理日志请求（用于异步处理）
func (s *LogService) HandleLog(c *gin.Context, logData LogData) {
	// 在goroutine中异步处理，不阻塞响应
	go func() {
		if err := s.SaveLog(logData); err != nil {
			fmt.Printf("异步保存日志失败: %v\n", err)
			// 不返回错误，避免影响主流程
		}
	}()
}
