package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handler"
	"homemoney/internal/service"
)

// SetupPaymentRoutes 设置支付相关的API路由
// 对应JS版本的支付功能路由
func SetupPaymentRoutes(router *gin.Engine, paymentService *service.PaymentService) {
	// 创建支付处理器
	paymentHandler := handler.NewPaymentHandler(paymentService)
	
	// 支付相关的API路由组
	payments := router.Group("/api/payments")
	{
		// 捐赠支付接口
		// 对应JS版本: POST /api/payments/donate
		payments.POST("/donate", paymentHandler.Donate)
		
		// 会员订阅支付接口
		// 对应JS版本: POST /api/payments/subscribe
		payments.POST("/subscribe", paymentHandler.SubscribePayment)
	}
}