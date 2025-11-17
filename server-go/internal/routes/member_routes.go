package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SetupMemberRoutes 配置会员相关的API路由
// 对应JS版本的memberRoutes功能
// 注意：这个函数应该在main.go中调用，并且需要传入数据库连接
func SetupMemberRoutes(router *gin.Engine, memberRepo *repository.MemberRepository, planRepo *repository.SubscriptionPlanRepository, subscriptionRepo *repository.UserSubscriptionRepository) {
	// 初始化Service层
	memberService := service.NewMemberService(memberRepo, subscriptionRepo)
	planService := service.NewSubscriptionPlanService(planRepo)
	subscriptionService := service.NewSubscriptionService(subscriptionRepo, planRepo, memberRepo)

	// 初始化Handler层
	memberHandler := handler.NewMemberHandler(memberService)
	subscriptionHandler := handler.NewSubscriptionHandler(subscriptionService, planService)

	// 创建会员相关的路由组 - 与JS版本完全一致
	memberGroup := router.Group("/api/members")

	// 对应JS版本: POST /api/members - 创建或获取会员
	memberGroup.POST("", memberHandler.GetOrCreateMember)

	// 对应JS版本: GET /api/members/:username - 获取会员信息
	memberGroup.GET(":username", memberHandler.GetMemberInfo)

	// 对应JS版本: PUT /api/members/:id/status - 更新会员状态
	memberGroup.PUT(":username/status", memberHandler.UpdateMemberStatus)

	// 对应JS版本: GET /api/members/:username/subscriptions - 获取会员订阅
	memberGroup.GET(":username/subscriptions", memberHandler.GetMemberSubscriptions)

	// 对应JS版本: GET /api/members/:username/current-subscription - 获取当前订阅
	memberGroup.GET(":username/current-subscription", subscriptionHandler.GetCurrentSubscription)

	// 对应JS版本: GET /api/members/subscription-plans - 获取订阅计划
	// 订阅计划路由挂载在 /api/members 下，与JS版本一致
	memberGroup.GET("/subscription-plans", subscriptionHandler.GetSubscriptionPlans)

	// 创建订阅相关的路由组
	subscriptionGroup := router.Group("/api/subscriptions")

	// 对应JS版本: POST /api/subscriptions - 创建订阅
	subscriptionGroup.POST("", subscriptionHandler.CreateSubscription)

	// 对应JS版本: DELETE /api/subscriptions/:id - 取消订阅
	subscriptionGroup.DELETE(":id", subscriptionHandler.CancelSubscription)

	// 对应JS版本: POST /api/subscriptions/:id/renew - 续费订阅
	subscriptionGroup.POST(":id/renew", subscriptionHandler.RenewSubscription)

	// 管理员功能路由
	adminGroup := router.Group("/api/admin")
	
	// 管理员获取所有订阅计划
	adminGroup.GET("/subscription-plans", subscriptionHandler.AdminGetAllPlans)
	
	// 管理员创建订阅计划
	adminGroup.POST("/subscription-plans", subscriptionHandler.AdminCreatePlan)

	// 系统维护路由（用于自动处理过期订阅等）
	maintenanceGroup := router.Group("/api/maintenance")
	
	// 检查订阅状态
	maintenanceGroup.GET("/check-subscriptions", subscriptionHandler.CheckSubscriptionStatus)
	
	// 处理自动续费
	maintenanceGroup.GET("/process-renewals", subscriptionHandler.ProcessAutoRenewals)
	
	// 获取即将过期的订阅
	maintenanceGroup.GET("/expiring-subscriptions", subscriptionHandler.GetExpiringSubscriptions)
}