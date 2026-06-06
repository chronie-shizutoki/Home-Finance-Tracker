package handler

import (
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SubscriptionHandler 订阅处理器
type SubscriptionHandler struct {
	subscriptionService *service.SubscriptionService
	planService         *service.SubscriptionPlanService
}

// NewSubscriptionHandler 创建新的订阅处理器
func NewSubscriptionHandler(subscriptionService *service.SubscriptionService, planService *service.SubscriptionPlanService) *SubscriptionHandler {
	return &SubscriptionHandler{
		subscriptionService: subscriptionService,
		planService:         planService,
	}
}

// GetPlans 获取所有订阅计划
func (h *SubscriptionHandler) GetPlans(c *gin.Context) {
	plans, err := h.planService.GetAllPlans()
	if err != nil {
		c.JSON(500, gin.H{"error": "获取订阅计划失败"})
		return
	}
	c.JSON(200, gin.H{"success": true, "data": plans})
}