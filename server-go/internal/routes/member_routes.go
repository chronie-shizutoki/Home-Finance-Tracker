package routes

import (
	"homemoney/internal/handler"
	"homemoney/internal/repository"
	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// SetupMemberRoutes configures member-related API routes
// Corresponds to JS version memberRoutes functionality
// Frontend API: POST /api/members, GET /api/members/members/:username, PUT /api/members/members/:username/avatar
func SetupMemberRoutes(router *gin.Engine, memberRepo *repository.MemberRepository) {
	// Initialize Service layer
	memberService := service.NewMemberService(memberRepo)

	// Initialize Handler layer
	memberHandler := handler.NewMemberHandler(memberService)

	// Create member-related route group - fully consistent with frontend API
	memberGroup := router.Group("/api/members")

	// Corresponds to frontend: POST /api/members/members - create or get member
	memberGroup.POST("/members", memberHandler.GetOrCreateMember)

	// Corresponds to frontend: GET /api/members/members/:username - get member info
	memberGroup.GET("/members/:username", memberHandler.GetMemberInfo)

	// Corresponds to frontend: PUT /api/members/members/:username/avatar - update avatar
	memberGroup.PUT("/members/:username/avatar", memberHandler.UpdateAvatar)

	// Admin functionality routes
	_ = router.Group("/api/admin")

	// System maintenance routes (for auto-processing expired subscriptions, etc.)
	_ = router.Group("/api/maintenance")
}