package handler

import (
	"net/http"
	"strconv"

	"homemoney/internal/models"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SubscriptionHandler 订阅处理程序
type SubscriptionHandler struct {
	subscriptionService *service.SubscriptionService
	planService         *service.SubscriptionPlanService
}

// NewSubscriptionHandler 创建新的订阅处理程序
func NewSubscriptionHandler(
	subscriptionService *service.SubscriptionService,
	planService *service.SubscriptionPlanService,
) *SubscriptionHandler {
	return &SubscriptionHandler{
		subscriptionService: subscriptionService,
		planService:         planService,
	}
}

// GetSubscriptionPlans 获取订阅计划列表 - 对应JS版本的 GET /subscription-plans
func (h *SubscriptionHandler) GetSubscriptionPlans(c *gin.Context) {
	plans, err := h.subscriptionService.GetSubscriptionPlans()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取订阅计划列表失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    plans,
		"total":   len(plans),
	})
}

// CreateSubscription 创建订阅 - 对应JS版本的 POST /subscriptions
func (h *SubscriptionHandler) CreateSubscription(c *gin.Context) {
	var request struct {
		Username  string `json:"username" binding:"required"`
		PlanID    string `json:"planId" binding:"required"`
		AutoRenew bool   `json:"autoRenew"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数验证失败",
			"message": err.Error(),
		})
		return
	}

	subscription, err := h.subscriptionService.CreateSubscription(request.Username, request.PlanID, request.AutoRenew)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "创建订阅失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"message": "订阅创建成功",
		"data":    subscription,
	})
}

// GetCurrentSubscription 获取当前订阅 - 对应JS版本的 GET /subscriptions/current
func (h *SubscriptionHandler) GetCurrentSubscription(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数错误",
			"message": "用户名不能为空",
		})
		return
	}

	subscription, err := h.subscriptionService.GetCurrentSubscription(username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取当前订阅失败",
			"message": err.Error(),
		})
		return
	}

	// 如果没有活跃订阅，返回空数据（与JS版本一致）
	if subscription == nil {
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"data":    nil,
			"message": "当前没有活跃订阅",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    subscription,
	})
}

// GetMemberSubscriptions 获取用户所有订阅记录 - 对应JS版本的 GET /subscriptions/:username
func (h *SubscriptionHandler) GetMemberSubscriptions(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数错误",
			"message": "用户名不能为空",
		})
		return
	}

	subscriptions, err := h.subscriptionService.GetMemberSubscriptions(username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取订阅列表失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    subscriptions,
		"total":   len(subscriptions),
	})
}

// CancelSubscription 取消订阅 - 对应JS版本的 DELETE /subscriptions/:id
func (h *SubscriptionHandler) CancelSubscription(c *gin.Context) {
	subscriptionID := c.Param("id")
	if subscriptionID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数错误",
			"message": "订阅ID不能为空",
		})
		return
	}

	if err := h.subscriptionService.CancelSubscription(subscriptionID); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "取消订阅失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "订阅取消成功",
		"data": gin.H{
			"id": subscriptionID,
		},
	})
}

// RenewSubscription 续费订阅
func (h *SubscriptionHandler) RenewSubscription(c *gin.Context) {
	subscriptionID := c.Param("id")
	if subscriptionID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数错误",
			"message": "订阅ID不能为空",
		})
		return
	}

	if err := h.subscriptionService.RenewSubscription(subscriptionID); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "续费订阅失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "订阅续费成功",
		"data": gin.H{
			"id": subscriptionID,
		},
	})
}

// CheckSubscriptionStatus 检查订阅状态
func (h *SubscriptionHandler) CheckSubscriptionStatus(c *gin.Context) {
	if err := h.subscriptionService.CheckExpiredSubscriptions(); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "检查订阅状态失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "订阅状态检查完成",
	})
}

// ProcessAutoRenewals 处理自动续费
func (h *SubscriptionHandler) ProcessAutoRenewals(c *gin.Context) {
	if err := h.subscriptionService.ProcessAutoRenewals(); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "处理自动续费失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "自动续费处理完成",
	})
}

// GetExpiringSubscriptions 获取即将过期的订阅
func (h *SubscriptionHandler) GetExpiringSubscriptions(c *gin.Context) {
	daysBefore := 7 // 默认7天
	if days := c.Query("days"); days != "" {
		if parsed, err := strconv.Atoi(days); err == nil && parsed > 0 {
			daysBefore = parsed
		}
	}

	subscriptions, err := h.subscriptionService.GetExpiringSubscriptions(daysBefore)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取即将过期订阅失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    subscriptions,
		"total":   len(subscriptions),
		"message": "获取即将在" + strconv.Itoa(daysBefore) + "天内过期的订阅",
	})
}

// AdminGetAllPlans 获取所有订阅计划（管理员功能）
func (h *SubscriptionHandler) AdminGetAllPlans(c *gin.Context) {
	plans, err := h.planService.GetAllPlans()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取订阅计划列表失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    plans,
		"total":   len(plans),
	})
}

// AdminCreatePlan 创建订阅计划（管理员功能）
func (h *SubscriptionHandler) AdminCreatePlan(c *gin.Context) {
	var request struct {
		Name        string  `json:"name" binding:"required"`
		Description string  `json:"description"`
		Duration    int     `json:"duration" binding:"required"`
		Price       float64 `json:"price" binding:"required"`
		Period      string  `json:"period" binding:"required"`
		IsActive    *bool   `json:"isActive"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数验证失败",
			"message": err.Error(),
		})
		return
	}

	plan := &models.SubscriptionPlan{
		Name:        request.Name,
		Description: request.Description,
		Duration:    request.Duration,
		Price:       request.Price,
		Period:      request.Period,
	}

	if err := h.planService.CreatePlan(plan); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "创建订阅计划失败",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"message": "订阅计划创建成功",
		"data":    plan,
	})
}