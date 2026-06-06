package utils

import (
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
)

// SuccessResponse 成功响应
func SuccessResponse(data interface{}) gin.H {
	return gin.H{
		"success": true,
		"data":    data,
	}
}

// ErrorResponse 错误响应
func ErrorResponse(message, error string) gin.H {
	return gin.H{
		"success": false,
		"error":   message,
		"details": error,
	}
}

// PaginatedResponse 分页响应
func PaginatedResponse(data interface{}, total int64, page, limit int) gin.H {
	totalPages := int((total + int64(limit) - 1) / int64(limit))
	return gin.H{
		"success": true,
		"data":    data,
		"total":   total,
		"page":    page,
		"limit":   limit,
		"pages":   totalPages,
	}
}

// ErrorResponseWithStatus 带状态码的错误响应
func ErrorResponseWithStatus(c *gin.Context, message, error string, statusCode int) {
	c.JSON(statusCode, ErrorResponse(message, error))
}

// ParseMonth 解析月份参数 "YYYY-MM"
func ParseMonth(month string) (time.Time, time.Time, error) {
	if month == "" {
		return time.Time{}, time.Time{}, nil
	}

	parsed, err := time.Parse("2006-01", month)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}

	startDate := time.Date(parsed.Year(), parsed.Month(), 1, 0, 0, 0, 0, time.UTC)
	endDate := startDate.AddDate(0, 1, -1)
	endDate = time.Date(endDate.Year(), endDate.Month(), endDate.Day(), 23, 59, 59, 999999999, time.UTC)

	return startDate, endDate, nil
}

// ParseAmount 解析金额参数
func ParseAmount(amountStr string) (*float64, error) {
	if amountStr == "" {
		return nil, nil
	}
	
	amount, err := ParseFloat(amountStr)
	if err != nil {
		return nil, err
	}
	return &amount, nil
}

// ParseFloat 解析字符串为浮点数
func ParseFloat(s string) (float64, error) {
	if s == "" {
		return 0, nil
	}
	
	var result float64
	_, err := fmt.Sscanf(s, "%f", &result)
	if err != nil {
		return 0, err
	}
	return result, nil
}

// ParseInt 解析字符串为整数
func ParseInt(s string) (int, error) {
	if s == "" {
		return 0, nil
	}
	
	var result int
	_, err := fmt.Sscanf(s, "%d", &result)
	if err != nil {
		return 0, err
	}
	return result, nil
}

// ValidateSort 验证排序参数
func ValidateSort(sort string) bool {
	validSorts := map[string]bool{
		"dateAsc":    true,
		"dateDesc":   true,
		"amountAsc":  true,
		"amountDesc": true,
	}
	return validSorts[sort]
}

// ValidatePagination 验证分页参数
func ValidatePagination(page, limit int) (int, int) {
	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 20
	}
	return page, limit
}