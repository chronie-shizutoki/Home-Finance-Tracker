package handler

import (
	"net/http"

	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// PaymentHandler 支付处理器
type PaymentHandler struct {
	paymentService *service.PaymentService
}

// NewPaymentHandler 创建支付处理器
func NewPaymentHandler(paymentService *service.PaymentService) *PaymentHandler {
	return &PaymentHandler{
		paymentService: paymentService,
	}
}

// Donate 处理捐赠请求
func (h *PaymentHandler) Donate(c *gin.Context) {
	var request struct {
		Username string  `json:"username" binding:"required"`
		Amount   float64 `json:"amount" binding:"required,gt=0"`
	}

	// 绑定请求参数
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "参数验证失败: " + err.Error(),
		})
		return
	}

	// 验证金额最多两位小数
	if !isValidAmount(request.Amount) {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "金额最多只能有两位小数",
		})
		return
	}

	// 调用服务处理捐赠
	response, err := h.paymentService.Donate(request.Username, request.Amount)
	if err != nil || !response.Success {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   response.Error,
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// SubscribePayment 处理订阅支付请求
func (h *PaymentHandler) SubscribePayment(c *gin.Context) {
	var request struct {
		Username string `json:"username" binding:"required"`
		PlanID   string `json:"planId" binding:"required"`
	}

	// 绑定请求参数
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "参数验证失败: " + err.Error(),
		})
		return
	}

	// 调用服务处理订阅支付
	response, err := h.paymentService.SubscribePayment(request.Username, request.PlanID)
	if err != nil || !response.Success {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   response.Error,
		})
		return
	}

	c.JSON(http.StatusOK, response)
}

// isValidAmount 验证金额是否有效（最多两位小数）
func isValidAmount(amount float64) bool {
	// 检查金额是否为整数或最多两位小数
	intAmount := int64(amount * 100)
	return float64(intAmount)/100 == amount
}