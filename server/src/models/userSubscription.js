const { DataTypes } = require('sequelize')

module.exports = (sequelize) => {
  const UserSubscription = sequelize.define('UserSubscription', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    memberId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'Members',
        key: 'id'
      }
    },
    planId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'SubscriptionPlans',
        key: 'id'
      }
    },
    startDate: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    endDate: {
      type: DataTypes.DATE,
      allowNull: false
    },
    status: {
      type: DataTypes.ENUM('active', 'expired', 'canceled'),
      allowNull: false,
      defaultValue: 'active'
    },
    paymentId: {
      type: DataTypes.STRING,
      allowNull: true,
      comment: '支付平台的交易ID'
    },
    autoRenew: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
      comment: '是否自动续费'
    },
    createdAt: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW,
      onUpdate: DataTypes.NOW
    }
  })

  // 添加虚拟字段，计算是否处于活跃状态
  UserSubscription.prototype.isActiveSubscription = function() {
    const now = new Date()
    return this.status === 'active' && now >= this.startDate && now <= this.endDate
  }

  return UserSubscription
}