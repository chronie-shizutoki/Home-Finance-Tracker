package repository

import (
	"fmt"

	"gorm.io/gorm"
	"homemoney/internal/models"
)

// SubscriptionPlanRepository 订阅计划数据仓库
type SubscriptionPlanRepository struct {
	db *gorm.DB
}

// NewSubscriptionPlanRepository 创建新的订阅计划仓库
func NewSubscriptionPlanRepository(db *gorm.DB) *SubscriptionPlanRepository {
	return &SubscriptionPlanRepository{
		db: db,
	}
}

// Create 创建订阅计划
func (r *SubscriptionPlanRepository) Create(plan *models.SubscriptionPlan) error {
	if plan.Name == "" {
		return fmt.Errorf("计划名称不能为空")
	}
	return r.db.Create(plan).Error
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

// GetAll 获取所有订阅计划
func (r *SubscriptionPlanRepository) GetAll() ([]models.SubscriptionPlan, error) {
	var plans []models.SubscriptionPlan
	if err := r.db.Order("price ASC").Find(&plans).Error; err != nil {
		return nil, err
	}
	return plans, nil
}

// GetActivePlans 获取所有活跃的订阅计划
func (r *SubscriptionPlanRepository) GetActivePlans() ([]models.SubscriptionPlan, error) {
	var plans []models.SubscriptionPlan
	if err := r.db.Where("is_active = ?", true).
		Order("price ASC").
		Find(&plans).Error; err != nil {
		return nil, err
	}
	return plans, nil
}

// Update 更新订阅计划
func (r *SubscriptionPlanRepository) Update(plan *models.SubscriptionPlan) error {
	if plan.ID == "" {
		return fmt.Errorf("计划ID不能为空")
	}
	return r.db.Save(plan).Error
}

// Delete 删除订阅计划
func (r *SubscriptionPlanRepository) Delete(id string) error {
	result := r.db.Delete(&models.SubscriptionPlan{}, "id = ?", id)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("计划不存在")
	}
	return nil
}

// Exists 检查计划是否存在
func (r *SubscriptionPlanRepository) Exists(id string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.SubscriptionPlan{}).Where("id = ?", id).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}