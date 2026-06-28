package service

import (
	"fmt"

	"homemoney/internal/models"
	"homemoney/internal/repository"
)

// MemberService member service
type MemberService struct {
	memberRepo *repository.MemberRepository
}

// NewMemberService creates a new member service
func NewMemberService(memberRepo *repository.MemberRepository) *MemberService {
	return &MemberService{
		memberRepo: memberRepo,
	}
}

// GetOrCreateMember gets or creates a member
func (s *MemberService) GetOrCreateMember(username string) (*models.Member, error) {
	if username == "" {
		return nil, fmt.Errorf("username cannot be empty")
	}
	return s.memberRepo.GetOrCreate(username)
}

// GetMemberInfo gets member info - fully consistent with JS version (no subscription info)
func (s *MemberService) GetMemberInfo(username string) (*models.Member, error) {
	member, err := s.memberRepo.GetMemberInfo(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("member not found")
	}

	return member, nil
}

// UpdateAvatar updates member avatar
func (s *MemberService) UpdateAvatar(username string, avatar string) (*models.Member, error) {
	member, err := s.memberRepo.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("member not found")
	}

	member.Avatar = &avatar
	if err := s.memberRepo.Update(member); err != nil {
		return nil, err
	}

	return member, nil
}