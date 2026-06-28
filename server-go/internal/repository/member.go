package repository

import (
	"fmt"

	"gorm.io/gorm"
	"homemoney/internal/models"
)

// MemberRepository member data repository
type MemberRepository struct {
	db *gorm.DB
}

// NewMemberRepository creates a new member repository
func NewMemberRepository(db *gorm.DB) *MemberRepository {
	return &MemberRepository{
		db: db,
	}
}

// Create creates a member
func (r *MemberRepository) Create(member *models.Member) error {
	if member.Username == "" {
		return fmt.Errorf("username cannot be empty")
	}
	return r.db.Create(member).Error
}

// FindByUsername finds a member by username
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

// FindByID finds a member by ID
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

// GetOrCreate gets or creates a member
func (r *MemberRepository) GetOrCreate(username string) (*models.Member, error) {
	if username == "" {
		return nil, fmt.Errorf("username cannot be empty")
	}

	// Try to find existing member first
	member, err := r.FindByUsername(username)
	if err != nil {
		return nil, err
	}
	if member != nil {
		return member, nil
	}

	// If not exists, create a new member
	member = &models.Member{
		Username: username,
	}
	if err := r.Create(member); err != nil {
		return nil, err
	}
	return member, nil
}

// GetMemberInfo gets member info
func (r *MemberRepository) GetMemberInfo(username string) (*models.Member, error) {
	var member models.Member
	if err := r.db.First(&member, "username = ?", username).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, err
	}
	return &member, nil
}

// Update updates a member
func (r *MemberRepository) Update(member *models.Member) error {
	if member.ID == "" {
		return fmt.Errorf("member ID cannot be empty")
	}
	return r.db.Save(member).Error
}

// Exists checks if a member exists
func (r *MemberRepository) Exists(username string) (bool, error) {
	var count int64
	if err := r.db.Model(&models.Member{}).Where("username = ?", username).Count(&count).Error; err != nil {
		return false, err
	}
	return count > 0, nil
}

// FindAll gets all members (for admin interface)
func (r *MemberRepository) FindAll() ([]models.Member, error) {
	var members []models.Member
	if err := r.db.Order("created_at DESC").Find(&members).Error; err != nil {
		return nil, err
	}
	return members, nil
}