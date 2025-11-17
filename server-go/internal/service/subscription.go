package service

import (
	"fmt"

	"homemoney/internal/models"
	"homemoney/internal/repository"
)

// SubscriptionPlanService 订阅计划服务
type SubscriptionPlanService struct {
	planRepo *repository.SubscriptionPlanRepository
}

// NewSubscriptionPlanService 创建新的订阅计划服务
func NewSubscriptionPlanService(planRepo *repository.SubscriptionPlanRepository) *SubscriptionPlanService {
	return &SubscriptionPlanService{
		planRepo: planRepo,
	}
}

// GetAllPlans 获取所有订阅计划
func (s *SubscriptionPlanService) GetAllPlans() ([]models.SubscriptionPlan, error) {
	return s.planRepo.GetAll()
}

// GetActivePlans 获取所有活跃的订阅计划
func (s *SubscriptionPlanService) GetActivePlans() ([]models.SubscriptionPlan, error) {
	return s.planRepo.GetActivePlans()
}

// CreatePlan 创建订阅计划
func (s *SubscriptionPlanService) CreatePlan(plan *models.SubscriptionPlan) error {
	if plan.Name == "" {
		return fmt.Errorf("计划名称不能为空")
	}
	if plan.Price <= 0 {
		return fmt.Errorf("价格必须大于0")
	}
	if plan.Duration <= 0 {
		return fmt.Errorf("订阅天数必须大于0")
	}
	
	// 设置默认状态
	if plan.Period == "" {
		plan.Period = "monthly"
	}
	
	return s.planRepo.Create(plan)
}

// UpdatePlan 更新订阅计划
func (s *SubscriptionPlanService) UpdatePlan(plan *models.SubscriptionPlan) error {
	return s.planRepo.Update(plan)
}

// DeletePlan 删除订阅计划
func (s *SubscriptionPlanService) DeletePlan(id string) error {
	if id == "" {
		return fmt.Errorf("计划ID不能为空")
	}
	return s.planRepo.Delete(id)
}

// TogglePlanStatus 切换订阅计划状态
func (s *SubscriptionPlanService) TogglePlanStatus(id string) error {
	plan, err := s.planRepo.FindByID(id)
	if err != nil {
		return err
	}
	if plan == nil {
		return fmt.Errorf("订阅计划不存在")
	}

	plan.IsActive = !plan.IsActive

	return s.planRepo.Update(plan)
}

// SubscriptionService 用户订阅服务
type SubscriptionService struct {
	subscriptionRepo *repository.UserSubscriptionRepository
	planRepo         *repository.SubscriptionPlanRepository
	memberRepo       *repository.MemberRepository
}

// NewSubscriptionService 创建新的用户订阅服务
func NewSubscriptionService(
	subscriptionRepo *repository.UserSubscriptionRepository,
	planRepo *repository.SubscriptionPlanRepository,
	memberRepo *repository.MemberRepository,
) *SubscriptionService {
	return &SubscriptionService{
		subscriptionRepo: subscriptionRepo,
		planRepo:         planRepo,
		memberRepo:       memberRepo,
	}
}

// GetSubscriptionPlans 获取订阅计划列表
func (s *SubscriptionService) GetSubscriptionPlans() ([]models.SubscriptionPlan, error) {
	return s.planRepo.GetActivePlans()
}

// CreateSubscription 创建订阅
func (s *SubscriptionService) CreateSubscription(username, planID string, autoRenew bool) (*models.UserSubscription, error) {
	// 验证用户存在
	member, err := s.memberRepo.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	// 验证计划存在且激活
	plan, err := s.planRepo.FindByID(planID)
	if err != nil {
		return nil, err
	}
	if plan == nil || !plan.IsActive {
		return nil, fmt.Errorf("订阅计划不存在或已停用")
	}

	// 检查是否已有活跃订阅
	currentSubscription, err := s.subscriptionRepo.GetCurrentSubscription(member.ID)
	if err != nil {
		return nil, err
	}
	if currentSubscription != nil {
		return nil, fmt.Errorf("用户已有活跃订阅")
	}

	// 创建新订阅
	subscription, err := s.subscriptionRepo.CreateSubscription(member.ID, planID, autoRenew)
	if err != nil {
		return nil, err
	}

	return subscription, nil
}

// GetCurrentSubscription 获取用户当前订阅
func (s *SubscriptionService) GetCurrentSubscription(username string) (*models.UserSubscription, error) {
	member, err := s.memberRepo.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	return s.subscriptionRepo.GetCurrentSubscription(member.ID)
}

// GetMemberSubscriptions 获取用户所有订阅记录
func (s *SubscriptionService) GetMemberSubscriptions(username string) ([]models.UserSubscription, error) {
	member, err := s.memberRepo.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, fmt.Errorf("会员不存在")
	}

	return s.subscriptionRepo.GetMemberSubscriptions(member.ID)
}

// CancelSubscription 取消订阅
func (s *SubscriptionService) CancelSubscription(subscriptionID string) error {
	return s.subscriptionRepo.CancelSubscription(subscriptionID)
}

// RenewSubscription 续费订阅
func (s *SubscriptionService) RenewSubscription(subscriptionID string) error {
	subscription, err := s.subscriptionRepo.FindByID(subscriptionID)
	if err != nil {
		return err
	}
	if subscription == nil {
		return fmt.Errorf("订阅记录不存在")
	}

	// 获取订阅计划
	plan, err := s.planRepo.FindByID(subscription.PlanID)
	if err != nil {
		return err
	}
	if plan == nil {
		return fmt.Errorf("订阅计划不存在")
	}

	// 计算新的订阅期间
	newEndDate := subscription.EndDate.AddDate(0, 0, plan.Duration)

	// 更新订阅
	subscription.EndDate = newEndDate
	subscription.Status = "active"

	return s.subscriptionRepo.Update(subscription)
}

// CheckExpiredSubscriptions 检查过期订阅
func (s *SubscriptionService) CheckExpiredSubscriptions() error {
	return s.subscriptionRepo.CheckAndExpireSubscriptions()
}

// ProcessAutoRenewals 处理自动续费
func (s *SubscriptionService) ProcessAutoRenewals() error {
	return s.subscriptionRepo.AutoRenewSubscriptions()
}

// GetExpiringSubscriptions 获取即将过期的订阅
func (s *SubscriptionService) GetExpiringSubscriptions(daysBefore int) ([]models.UserSubscription, error) {
	return s.subscriptionRepo.GetExpiringSubscriptions(daysBefore)
}