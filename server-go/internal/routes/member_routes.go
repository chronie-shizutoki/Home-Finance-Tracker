package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SetupMemberRoutes 配置会员相关的API路由
// 对应JS版本的memberRoutes功能
func SetupMemberRoutes(router *gin.Engine, memberRepo *repository.MemberRepository) {
	// 初始化Service层
	memberService := service.NewMemberService(memberRepo)

	// 初始化Handler层
	memberHandler := handler.NewMemberHandler(memberService)

	// 创建会员相关的路由组 - 与JS版本完全一致
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
}