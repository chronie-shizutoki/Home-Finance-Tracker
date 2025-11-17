package repository

import (
	"fmt"

	"gorm.io/gorm"
	"homemoney/internal/models"
)

// MemberRepository 会员数据仓库
type MemberRepository struct {
	db *gorm.DB
}

// NewMemberRepository 创建新的会员仓库
func NewMemberRepository(db *gorm.DB) *MemberRepository {
	return &MemberRepository{
		db: db,
	}
}

// Create 创建会员
func (r *MemberRepository) Create(member *models.Member) error {
	if member.Username == "" {
		return fmt.Errorf("用户名不能为空")
	}
	return r.db.Create(member).Error
}

// FindByUsername 根据用户名查找会员
func (r *MemberRepository) FindByUsername(username string) (*models.Member, error) {
	var member models.Member
	if err := r.db.First(&member, "username = ?", username).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &member, nil
}

// FindByID 根据ID查找会员
func (r *MemberRepository) FindByID(id string) (*models.Member, error) {
	var member models.Member
	if err := r.db.First(&member, "id = ?", id).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &member, nil
}

// GetOrCreate 获取或创建会员
func (r *MemberRepository) GetOrCreate(username string) (*models.Member, error) {
	if username == "" {
		return nil, fmt.Errorf("用户名不能为空")
	}

	// 先尝试查找现有会员
	member, err := r.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member != nil {
		return member, nil
	}

	// 如果不存在，创建新会员
	member = &models.Member{
		Username: username,
		IsActive: false,
	}
	if err := r.Create(member); err != nil {
		return nil, err
	}
	return member, nil
}

// GetMemberInfo 获取会员信息（包含订阅信息）
func (r *MemberRepository) GetMemberInfo(username string) (*models.Member, error) {
	var member models.Member
	if err := r.db.Preload("UserSubscriptions").First(&member, "username = ?", username).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &member, nil
}

// Update 更新会员
func (r *MemberRepository) Update(member *models.Member) error {
	if member.ID == "" {
		return fmt.Errorf("会员ID不能为空")
	}
	return r.db.Save(member).Error
}

// UpdateMemberStatus 更新会员状态
func (r *MemberRepository) UpdateMemberStatus(username string, isActive bool) error {
	member, err := r.FindByUsername(username)
	if err != nil {
		return err
	}
	if member == nil {
		return fmt.Errorf("会员不存在")
	}

	member.IsActive = isActive
	return r.db.Save(member).Error
}

// GetMemberSubscriptions 获取会员订阅列表
func (r *MemberRepository) GetMemberSubscriptions(username string) ([]models.UserSubscription, error) {
	var member models.Member
	if err := r.db.First(&member, "username = ?", username).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}

	var subscriptions []models.UserSubscription
	if err := r.db.Where("member_id = ?", member.ID).
		Preload("Plan").
		Order("created_at DESC").
		Find(&subscriptions).Error; err != nil {
		return nil, err
	}

	return subscriptions, nil
}

// Exists 检查会员是否存在
func (r *MemberRepository) Exists(username string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Member{}).Where("username = ?", username).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

// FindAll 获取所有会员（用于管理界面）
func (r *MemberRepository) FindAll() ([]models.Member, error) {
	var members []models.Member
	if err := r.db.Order("created_at DESC").Find(&members).Error; err != nil {
		return nil, err
	}
	return members, nil
}

// GetMemberWithActiveSubscription 获取会员及其当前订阅信息
func (r *MemberRepository) GetMemberWithActiveSubscription(username string) (*models.Member, *models.UserSubscription, error) {
	var member models.Member

	// 获取会员信息
	if err := r.db.First(&member, "username = ?", username).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil, nil
		}
		return nil, nil, err
	}

	// 获取当前活跃订阅
	var activeSubscription models.UserSubscription
	now := r.db.NowFunc()
	err := r.db.Where("member_id = ? AND status = ? AND start_date <= ? AND end_date >= ?",
		member.ID, "active", now, now).
		Preload("Plan").
		First(&activeSubscription).Error

	if err != nil && err != gorm.ErrRecordNotFound {
		return nil, nil, err
	}

	return &member, &activeSubscription, nil
}