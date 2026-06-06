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

// GetMemberInfo 获取会员信息 - 与JS版本完全一致（不包含订阅信息）
func (s *MemberService) GetMemberInfo(username string) (*models.Member, error) {
	member, err := s.memberRepo.GetMemberInfo(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	return member, nil
}

// UpdateAvatar 更新会员头像
func (s *MemberService) UpdateAvatar(username string, avatar string) (*models.Member, error) {
	member, err := s.memberRepo.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	member.Avatar = &avatar
	if err := s.memberRepo.Update(member); err != nil {
		return nil, err
	}

	return member, nil
}