package handler

import (
	"net/http"

	"homemoney/internal/service"

	"github.com/gin-gonic/gin"
)

// MemberHandler 会员处理程序
type MemberHandler struct {
	memberService *service.MemberService
}

// NewMemberHandler 创建新的会员处理程序
func NewMemberHandler(memberService *service.MemberService) *MemberHandler {
	return &MemberHandler{
		memberService: memberService,
	}
}

// GetOrCreateMember 获取或创建会员 - 对应JS版本的 POST /members
func (h *MemberHandler) GetOrCreateMember(c *gin.Context) {
	var request struct {
		Username string `json:"username" binding:"required"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数验证失败",
			"message": err.Error(),
		})
		return
	}

	member, err := h.memberService.GetOrCreateMember(request.Username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "获取或创建会员失败",
			"message": err.Error(),
		})
		return
	}

	// 返回与JS版本一致的格式
	memberResponse := struct {
		ID       string `json:"id"`
		Username string `json:"username"`
		IsActive bool   `json:"isActive"`
		CreatedAt interface{} `json:"createdAt"`
		UpdatedAt interface{} `json:"updatedAt"`
	}{
		ID:       member.ID,
		Username: member.Username,
		IsActive: member.IsActive,
	}

	// 处理时间字段，确保与JS版本一致
	if !member.CreatedAt.IsZero() {
		memberResponse.CreatedAt = member.CreatedAt
	}
	if !member.UpdatedAt.IsZero() {
		memberResponse.UpdatedAt = member.UpdatedAt
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    memberResponse,
	})
}

// GetMemberInfo 获取会员信息 - 对应JS版本的 GET /members/:id
func (h *MemberHandler) GetMemberInfo(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "参数错误",
			"message": "用户名不能为空",
		})
		return
	}

	memberInfo, err := h.memberService.GetMemberInfo(username)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{
			"error":   "会员不存在",
			"message": err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    memberInfo,
	})
}

// UpdateAvatar 更新会员头像 - 对应JS版本的 PUT /members/:username/avatar
func (h *MemberHandler) UpdateAvatar(c *gin.Context) {
	username := c.Param("username")
	if username == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "用户名不能为空",
		})
		return
	}

	var request struct {
		Avatar string `json:"avatar" binding:"required"`
	}
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "用户名和头像数据不能为空",
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

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    member,
	})
}