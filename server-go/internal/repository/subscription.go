package repository

import (
	"homemoney/internal/models"

	"gorm.io/gorm"
)

// SubscriptionPlanRepository 订阅计划仓库
type SubscriptionPlanRepository struct {
	db *gorm.DB
}

// NewSubscriptionPlanRepository 创建新的订阅计划仓库
func NewSubscriptionPlanRepository(db *gorm.DB) *SubscriptionPlanRepository {
	return &SubscriptionPlanRepository{db: db}
}

// FindAll 获取所有订阅计划
func (r *SubscriptionPlanRepository) FindAll() ([]models.SubscriptionPlan, error) {
	var plans []models.SubscriptionPlan
	if err := r.db.Find(&plans).Error; err != nil {
		return nil, err
	}
	return plans, nil
}

// FindByID 根据ID查找订阅计划
func (r *SubscriptionPlanRepository) FindByID(id string) (*models.SubscriptionPlan, error) {
	var plan models.SubscriptionPlan
	if err := r.db.First(&plan, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &plan, nil
}

// UserSubscriptionRepository 用户订阅仓库
type UserSubscriptionRepository struct {
	db *gorm.DB
}

// NewUserSubscriptionRepository 创建新的用户订阅仓库
func NewUserSubscriptionRepository(db *gorm.DB) *UserSubscriptionRepository {
	return &UserSubscriptionRepository{db: db}
}

// FindByMemberID 根据会员ID查找订阅
func (r *UserSubscriptionRepository) FindByMemberID(memberID string) ([]models.UserSubscription, error) {
	var subscriptions []models.UserSubscription
	if err := r.db.Where("member_id = ?", memberID).Order("created_at DESC").Find(&subscriptions).Error; err != nil {
		return nil, err
	}
	return subscriptions, nil
}

// FindActiveByMemberID 查找会员的活跃订阅
func (r *UserSubscriptionRepository) FindActiveByMemberID(memberID string) (*models.UserSubscription, error) {
	var subscription models.UserSubscription
	if err := r.db.Where("member_id = ? AND is_active = ?", memberID, true).First(&subscription).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &subscription, nil
}