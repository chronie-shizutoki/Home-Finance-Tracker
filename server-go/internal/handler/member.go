package handler

import (
	"net/http"

	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// MemberHandler member handler
type MemberHandler struct {
	memberService *service.MemberService
}

// NewMemberHandler creates a new member handler
func NewMemberHandler(memberService *service.MemberService) *MemberHandler {
	return &MemberHandler{
		memberService: memberService,
	}
}

// GetOrCreateMember gets or creates a member - corresponds to JS version POST /members
func (h *MemberHandler) GetOrCreateMember(c *gin.Context) {
	var request struct {
		Username string `json:"username" binding:"required"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Username cannot be empty",
		})
		return
	}

	member, err := h.memberService.GetOrCreateMember(request.Username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Server error",
		})
		return
	}

	// Return format consistent with JS version
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    member,
	})
}

// GetMemberInfo gets member info - corresponds to JS version GET /members/:username
func (h *MemberHandler) GetMemberInfo(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Username cannot be empty",
		})
		return
	}

	member, err := h.memberService.GetMemberInfo(username)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{
			"error": err.Error(),
		})
		return
	}

	// Return format consistent with JS version
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    member,
	})
}

// UpdateAvatar updates member avatar - corresponds to JS version PUT /members/:username/avatar
func (h *MemberHandler) UpdateAvatar(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Username cannot be empty",
		})
		return
	}

	var request struct {
		Avatar string `json:"avatar" binding:"required"`
	}
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Username and avatar data cannot be empty",
		})
		return
	}

	member, err := h.memberService.UpdateAvatar(username, request.Avatar)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{
			"error": err.Error(),
		})
		return
	}

	// Return format consistent with JS version
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    member,
	})
}