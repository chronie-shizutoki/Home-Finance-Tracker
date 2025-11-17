package handlers

import (
	"fmt"
	"net/http"
	"strconv"
	"time"

	"homemoney/internal/models"
	"homemoney/internal/repository"
	"homemoney/pkg/utils"
	"github.com/gin-gonic/gin"
)

// ExpenseHandler 消费记录处理器
type ExpenseHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewExpenseHandler 创建新的expense处理器
func NewExpenseHandler(expenseRepo *repository.ExpenseRepository) *ExpenseHandler {
	return &ExpenseHandler{
		expenseRepo: expenseRepo,
	}
}

// GetExpenses 获取消费记录列表
func (h *ExpenseHandler) GetExpenses(c *gin.Context) {
	// 解析查询参数
	query, err := h.parseExpenseQuery(c)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "读取数据失败", err.Error(), http.StatusInternalServerError)
		return
	}

	// 执行查询
	expenses, total, err := h.expenseRepo.FindWithPagination(query)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "读取数据失败", err.Error(), http.StatusInternalServerError)
		return
	}

	// 获取元数据
	meta, err := h.expenseRepo.GetMeta()
	if err != nil {
		// 元数据获取失败不影响主要功能
		meta = &models.ExpenseMeta{}
	}

	// 返回格式与Node.js完全一致
	page := query.Offset/query.Limit + 1
	c.JSON(http.StatusOK, gin.H{
		"data":  expenses,
		"total": total,
		"page":  page,
		"limit": query.Limit,
		"meta":  meta,
	})
}

// CreateExpense 创建消费记录
func (h *ExpenseHandler) CreateExpense(c *gin.Context) {
	var expense models.Expense
	if err := c.ShouldBindJSON(&expense); err != nil {
		utils.ErrorResponseWithStatus(c, "消费类型和金额是必填项", err.Error(), http.StatusBadRequest)
		return
	}

	// 后端数据验证
	if expense.Type == "" || expense.Amount <= 0 {
		utils.ErrorResponseWithStatus(c, "消费类型和金额是必填项", "", http.StatusBadRequest)
		return
	}

	// 保存记录
	if err := h.expenseRepo.Create(&expense); err != nil {
		utils.ErrorResponseWithStatus(c, "无法添加记录", err.Error(), http.StatusInternalServerError)
		return
	}

	// 返回格式与Node.js完全一致 - 直接返回创建的对象
	c.JSON(http.StatusCreated, expense)
}

// GetExpenseStatistics 获取消费统计
func (h *ExpenseHandler) GetExpenseStatistics(c *gin.Context) {
	query, err := h.parseExpenseQuery(c)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "获取统计数据失败", err.Error(), http.StatusInternalServerError)
		return
	}

	stats, err := h.expenseRepo.GetStatistics(query)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "获取统计数据失败", err.Error(), http.StatusInternalServerError)
		return
	}

	// 返回格式与Node.js完全一致
	c.JSON(http.StatusOK, stats)
}

// DeleteExpense 删除消费记录
func (h *ExpenseHandler) DeleteExpense(c *gin.Context) {
	id := c.Param("id")
	
	// 检查记录是否存在
	exists, err := h.expenseRepo.Exists(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "读取数据失败", err.Error(), http.StatusInternalServerError)
		return
	}
	if !exists {
		utils.ErrorResponseWithStatus(c, "记录不存在", "", http.StatusNotFound)
		return
	}

	// 删除记录
	if err := h.expenseRepo.Delete(id); err != nil {
		utils.ErrorResponseWithStatus(c, "读取数据失败", err.Error(), http.StatusInternalServerError)
		return
	}

	// 返回格式与Node.js完全一致 - 仅返回状态码
	c.Status(http.StatusNoContent)
}

// UpdateExpense 更新消费记录（需要先添加这个功能）
func (h *ExpenseHandler) UpdateExpense(c *gin.Context) {
	id := c.Param("id")
	
	// 查找现有记录
	expense, err := h.expenseRepo.FindByID(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "查找记录失败", err.Error(), http.StatusInternalServerError)
		return
	}
	if expense == nil {
		utils.ErrorResponseWithStatus(c, "记录不存在", "", http.StatusNotFound)
		return
	}

	// 解析请求数据
	var updateData models.Expense
	if err := c.ShouldBindJSON(&updateData); err != nil {
		utils.ErrorResponseWithStatus(c, "请求参数错误", err.Error(), http.StatusBadRequest)
		return
	}

	// 更新字段
	expense.Type = updateData.Type
	expense.Remark = updateData.Remark
	expense.Amount = updateData.Amount
	expense.Time = updateData.Time

	// 保存更新
	if err := h.expenseRepo.Update(expense); err != nil {
		utils.ErrorResponseWithStatus(c, "更新记录失败", err.Error(), http.StatusInternalServerError)
		return
	}

	c.JSON(http.StatusOK, utils.SuccessResponse(expense))
}

// parseExpenseQuery 解析expense查询参数
func (h *ExpenseHandler) parseExpenseQuery(c *gin.Context) (*models.ExpenseQuery, error) {
	query := &models.ExpenseQuery{}

	// 解析基础参数
	query.Keyword = c.Query("keyword")
	query.Type = c.Query("type")
	query.Month = c.Query("month")

	// 解析分页参数
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))
	query.Limit = limit
	query.Offset = (page - 1) * limit

	// 解析排序参数
	query.Sort = c.DefaultQuery("sort", "dateDesc")

	// 解析金额参数
	if minAmountStr := c.Query("minAmount"); minAmountStr != "" {
		if minAmount, err := strconv.ParseFloat(minAmountStr, 64); err == nil {
			query.MinAmount = &minAmount
		}
	}
	if maxAmountStr := c.Query("maxAmount"); maxAmountStr != "" {
		if maxAmount, err := strconv.ParseFloat(maxAmountStr, 64); err == nil {
			query.MaxAmount = &maxAmount
		}
	}

	// 解析日期参数
	if startDateStr := c.Query("startDate"); startDateStr != "" {
		if startDate, err := time.Parse(time.RFC3339, startDateStr); err == nil {
			query.StartDate = &startDate
		}
	}
	if endDateStr := c.Query("endDate"); endDateStr != "" {
		if endDate, err := time.Parse(time.RFC3339, endDateStr); err == nil {
			query.EndDate = &endDate
		}
	}

	// 如果提供了month，将其转换为日期范围
	if query.Month != "" {
		startDate, endDate, err := query.ToMonthRange()
		if err != nil {
			return nil, fmt.Errorf("月份解析失败: %w", err)
		}
		query.StartDate = &startDate
		query.EndDate = &endDate
	}

	// 验证查询参数
	if err := query.Validate(); err != nil {
		return nil, fmt.Errorf("查询参数验证失败: %w", err)
	}

	return query, nil
}

// TestHandler 测试端点，用于验证API行为
func (h *ExpenseHandler) TestHandler(c *gin.Context) {
	// 简单的健康检查端点
	c.JSON(http.StatusOK, gin.H{
		"message": "Expense API is working",
		"time":    time.Now(),
	})
}

// BatchCreateExpense 批量创建消费记录
func (h *ExpenseHandler) BatchCreateExpense(c *gin.Context) {
	var expenses []models.Expense
	if err := c.ShouldBindJSON(&expenses); err != nil {
		utils.ErrorResponseWithStatus(c, "请求参数错误", err.Error(), http.StatusBadRequest)
		return
	}

	if len(expenses) == 0 {
		utils.ErrorResponseWithStatus(c, "消费记录列表不能为空", "", http.StatusBadRequest)
		return
	}

	// 批量创建
	if err := h.expenseRepo.BatchCreate(expenses); err != nil {
		utils.ErrorResponseWithStatus(c, "批量创建失败", err.Error(), http.StatusInternalServerError)
		return
	}

	c.JSON(http.StatusCreated, utils.SuccessResponse(gin.H{
		"message": fmt.Sprintf("成功创建 %d 条消费记录", len(expenses)),
		"count":   len(expenses),
	}))
}

// GetExpenseByID 根据ID获取消费记录
func (h *ExpenseHandler) GetExpenseByID(c *gin.Context) {
	id := c.Param("id")
	
	expense, err := h.expenseRepo.FindByID(id)
	if err != nil {
		utils.ErrorResponseWithStatus(c, "查找记录失败", err.Error(), http.StatusInternalServerError)
		return
	}
	if expense == nil {
		utils.ErrorResponseWithStatus(c, "记录不存在", "", http.StatusNotFound)
		return
	}

	c.JSON(http.StatusOK, utils.SuccessResponse(expense))
}
