package repository

import (
	"fmt"
	"time"

	"gorm.io/gorm"
	"homemoney/internal/models"
)

// UserSubscriptionRepository 用户订阅数据仓库
type UserSubscriptionRepository struct {
	db *gorm.DB
}

// NewUserSubscriptionRepository 创建新的用户订阅仓库
func NewUserSubscriptionRepository(db *gorm.DB) *UserSubscriptionRepository {
	return &UserSubscriptionRepository{
		db: db,
	}
}

// Create 创建用户订阅
func (r *UserSubscriptionRepository) Create(subscription *models.UserSubscription) error {
	if subscription.MemberID == "" {
		return fmt.Errorf("会员ID不能为空")
	}
	if subscription.PlanID == "" {
		return fmt.Errorf("计划ID不能为空")
	}
	return r.db.Create(subscription).Error
}

// FindByID 根据ID查找用户订阅
func (r *UserSubscriptionRepository) FindByID(id string) (*models.UserSubscription, error) {
	var subscription models.UserSubscription
	if err := r.db.Preload("Plan").First(&subscription, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &subscription, nil
}

// GetCurrentSubscription 获取用户当前活跃订阅
func (r *UserSubscriptionRepository) GetCurrentSubscription(memberID string) (*models.UserSubscription, error) {
	var subscription models.UserSubscription
	now := time.Now()

	if err := r.db.Where("member_id = ? AND status = ? AND start_date <= ? AND end_date >= ?",
		memberID, "active", now, now).
		Preload("Plan").
		First(&subscription).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &subscription, nil
}

// GetMemberSubscriptions 获取用户的所有订阅记录
func (r *UserSubscriptionRepository) GetMemberSubscriptions(memberID string) ([]models.UserSubscription, error) {
	var subscriptions []models.UserSubscription
	if err := r.db.Where("member_id = ?", memberID).
		Preload("Plan").
		Order("created_at DESC").
		Find(&subscriptions).Error; err != nil {
		return nil, err
	}
	return subscriptions, nil
}

// CreateSubscription 创建订阅（带自动计算结束时间）
func (r *UserSubscriptionRepository) CreateSubscription(memberID, planID string, autoRenew bool) (*models.UserSubscription, error) {
	// 获取订阅计划
	planRepo := NewSubscriptionPlanRepository(r.db)
	plan, err := planRepo.FindByID(planID)
	if err != nil {
		return nil, err
	}
	if plan == nil {
		return nil, fmt.Errorf("订阅计划不存在")
	}

	// 计算订阅结束时间
	startDate := time.Now()
	endDate := startDate.AddDate(0, 0, plan.Duration)

	// 创建订阅
	subscription := &models.UserSubscription{
		MemberID:   memberID,
		PlanID:     planID,
		StartDate:  startDate,
		EndDate:    endDate,
		Status:     "active",
		AutoRenew:  autoRenew,
		PaymentID:  "",
	}

	if err := r.Create(subscription); err != nil {
		return nil, err
	}
	return subscription, nil
}

// CancelSubscription 取消订阅
func (r *UserSubscriptionRepository) CancelSubscription(subscriptionID string) error {
	subscription, err := r.FindByID(subscriptionID)
	if err != nil {
		return err
	}
	if subscription == nil {
		return fmt.Errorf("订阅记录不存在")
	}

	// 更新订阅状态为已取消
	subscription.Status = "canceled"
	return r.db.Save(subscription).Error
}

// ExpireSubscription 使订阅过期
func (r *UserSubscriptionRepository) ExpireSubscription(subscriptionID string) error {
	subscription, err := r.FindByID(subscriptionID)
	if err != nil {
		return err
	}
	if subscription == nil {
		return fmt.Errorf("订阅记录不存在")
	}

	// 更新订阅状态为已过期
	subscription.Status = "expired"
	return r.db.Save(subscription).Error
}

// Update 更新订阅
func (r *UserSubscriptionRepository) Update(subscription *models.UserSubscription) error {
	if subscription.ID == "" {
		return fmt.Errorf("订阅ID不能为空")
	}
	return r.db.Save(subscription).Error
}

// CheckAndExpireSubscriptions 检查并处理过期订阅
func (r *UserSubscriptionRepository) CheckAndExpireSubscriptions() error {
	now := time.Now()
	
	// 查找所有已过期但状态仍为活跃的订阅
	var expiredSubscriptions []models.UserSubscription
	if err := r.db.Where("status = ? AND end_date < ?", "active", now).
		Find(&expiredSubscriptions).Error; err != nil {
		return err
	}

	// 更新过期订阅的状态
	for _, subscription := range expiredSubscriptions {
		subscription.Status = "expired"
		if err := r.db.Save(&subscription).Error; err != nil {
			return err
		}
	}

	return nil
}

// GetExpiringSubscriptions 获取即将过期的订阅
func (r *UserSubscriptionRepository) GetExpiringSubscriptions(daysBefore int) ([]models.UserSubscription, error) {
	var subscriptions []models.UserSubscription
	expiryDate := time.Now().AddDate(0, 0, daysBefore)

	if err := r.db.Where("status = ? AND end_date <= ? AND end_date > ?",
		"active", expiryDate, time.Now()).
		Preload("Plan").
		Find(&subscriptions).Error; err != nil {
		return nil, err
	}
	return subscriptions, nil
}

// AutoRenewSubscriptions 自动续费订阅
func (r *UserSubscriptionRepository) AutoRenewSubscriptions() error {
	now := time.Now()
	
	// 查找需要自动续费的订阅
	var autoRenewSubscriptions []models.UserSubscription
	if err := r.db.Where("status = ? AND auto_renew = ? AND end_date < ?",
		"active", true, now).
		Find(&autoRenewSubscriptions).Error; err != nil {
		return err
	}

	// 续费每个订阅
	for _, subscription := range autoRenewSubscriptions {
		if err := r.renewSubscription(&subscription); err != nil {
			return err
		}
	}

	return nil
}

// renewSubscription 续费单个订阅
func (r *UserSubscriptionRepository) renewSubscription(subscription *models.UserSubscription) error {
	// 获取订阅计划以计算新的结束时间
	planRepo := NewSubscriptionPlanRepository(r.db)
	plan, err := planRepo.FindByID(subscription.PlanID)
	if err != nil {
		return err
	}
	if plan == nil {
		return fmt.Errorf("订阅计划不存在")
	}

	// 计算新的订阅期间
	newStartDate := subscription.EndDate
	newEndDate := newStartDate.AddDate(0, 0, plan.Duration)

	// 更新订阅
	subscription.StartDate = newStartDate
	subscription.EndDate = newEndDate
	subscription.Status = "active"

	return r.db.Save(subscription).Error
}

// Exists 检查订阅是否存在
func (r *UserSubscriptionRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.UserSubscription{}).Where("id = ?", id).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}