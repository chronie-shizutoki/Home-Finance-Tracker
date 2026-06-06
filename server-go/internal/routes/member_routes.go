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
	// JS版本: app.use('/api/members', router) 其中 router.post('/members', ...)
	// 所以完整路径为: POST /api/members/members
	memberGroup := router.Group("/api/members/members")

	// 对应JS版本: POST /api/members/members - 创建或获取会员
	memberGroup.POST("", memberHandler.GetOrCreateMember)

	// 对应JS版本: GET /api/members/members/:username - 获取会员信息
	memberGroup.GET("/:username", memberHandler.GetMemberInfo)

	// 对应JS版本: PUT /api/members/members/:username/avatar - 更新头像
	memberGroup.PUT("/:username/avatar", memberHandler.UpdateAvatar)

	// 管理员功能路由
	_ = router.Group("/api/admin")

	// 系统维护路由（用于自动处理过期订阅等）
	_ = router.Group("/api/maintenance")

	// 使用subscriptionHandler避免未使用错误
	_ = subscriptionHandler
}