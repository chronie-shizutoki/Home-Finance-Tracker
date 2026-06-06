package service

import (
	"homemoney/internal/models"
	"homemoney/internal/repository"
)

// SubscriptionPlanService 订阅计划服务
type SubscriptionPlanService struct {
	planRepo *repository.SubscriptionPlanRepository
}

// NewSubscriptionPlanService 创建新的订阅计划服务
func NewSubscriptionPlanService(planRepo *repository.SubscriptionPlanRepository) *SubscriptionPlanService {
	return &SubscriptionPlanService{planRepo: planRepo}
}

// GetAllPlans 获取所有订阅计划
func (s *SubscriptionPlanService) GetAllPlans() ([]models.SubscriptionPlan, error) {
	return s.planRepo.FindAll()
}

// SubscriptionService 订阅服务
type SubscriptionService struct {
	subscriptionRepo *repository.UserSubscriptionRepository
	planRepo         *repository.SubscriptionPlanRepository
	memberRepo       *repository.MemberRepository
}

// NewSubscriptionService 创建新的订阅服务
func NewSubscriptionService(subscriptionRepo *repository.UserSubscriptionRepository, planRepo *repository.SubscriptionPlanRepository, memberRepo *repository.MemberRepository) *SubscriptionService {
	return &SubscriptionService{
		subscriptionRepo: subscriptionRepo,
		planRepo:         planRepo,
		memberRepo:       memberRepo,
	}
}

// GetActiveSubscription 获取会员的活跃订阅
func (s *SubscriptionService) GetActiveSubscription(memberID string) (*models.UserSubscription, error) {
	return s.subscriptionRepo.FindActiveByMemberID(memberID)
}