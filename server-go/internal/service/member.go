package service

import (
	"fmt"

	"homemoney/internal/models"
	"homemoney/internal/repository"
)

// MemberService 会员服务
type MemberService struct {
	memberRepo       *repository.MemberRepository
	subscriptionRepo *repository.UserSubscriptionRepository
}

// NewMemberService 创建新的会员服务
func NewMemberService(memberRepo *repository.MemberRepository, subscriptionRepo *repository.UserSubscriptionRepository) *MemberService {
	return &MemberService{
		memberRepo:       memberRepo,
		subscriptionRepo: subscriptionRepo,
	}
}

// GetOrCreateMember 获取或创建会员
func (s *MemberService) GetOrCreateMember(username string) (*models.Member, error) {
	if username == "" {
		return nil, fmt.Errorf("用户名不能为空")
	}
	return s.memberRepo.GetOrCreate(username)
}

// GetMemberInfo 获取会员信息
func (s *MemberService) GetMemberInfo(username string) (*models.MemberResponse, error) {
	member, err := s.memberRepo.GetMemberInfo(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	// 获取当前订阅状态
	currentSubscription, err := s.subscriptionRepo.GetCurrentSubscription(member.ID)
	if err != nil {
		return nil, err
	}

	// 构建响应
	response := &models.MemberResponse{
		ID:                member.ID,
		Username:          member.Username,
		IsActive:          member.IsActive,
		CurrentSubscription: currentSubscription,
	}

	// 添加时间字段（如果需要）
	if !member.CreatedAt.IsZero() {
		response.CreatedAt = &member.CreatedAt
	}
	if !member.UpdatedAt.IsZero() {
		response.UpdatedAt = &member.UpdatedAt
	}

	return response, nil
}

// UpdateMemberStatus 更新会员状态
func (s *MemberService) UpdateMemberStatus(username string, isActive bool) error {
	return s.memberRepo.UpdateMemberStatus(username, isActive)
}

// GetMemberSubscriptions 获取会员订阅列表
func (s *MemberService) GetMemberSubscriptions(username string) ([]models.UserSubscription, error) {
	return s.memberRepo.GetMemberSubscriptions(username)
}

// GetMemberWithActiveSubscription 获取会员及其活跃订阅
func (s *MemberService) GetMemberWithActiveSubscription(username string) (*models.Member, *models.UserSubscription, error) {
	return s.memberRepo.GetMemberWithActiveSubscription(username)
}

// GetMemberList 获取会员列表（用于管理界面）
func (s *MemberService) GetMemberList() ([]models.Member, error) {
	return s.memberRepo.FindAll()
}

// CheckSubscriptionStatus 检查并更新订阅状态
func (s *MemberService) CheckSubscriptionStatus() error {
	// 检查并使过期订阅失效
	return s.subscriptionRepo.CheckAndExpireSubscriptions()
}

// ProcessAutoRenewal 处理自动续费
func (s *MemberService) ProcessAutoRenewal() error {
	return s.subscriptionRepo.AutoRenewSubscriptions()
}