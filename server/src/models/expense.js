const { DataTypes } = require('sequelize')

module.exports = (sequelize) => {
  const Expense = sequelize.define('Expense', {
    type: {
      type: DataTypes.STRING,
      allowNull: false
    },
    remark: {
      type: DataTypes.STRING,
      allowNull: true
    },
    amount: {
      type: DataTypes.FLOAT,
      allowNull: false
    },
    date: {
      type: DataTypes.STRING,
      allowNull: false
    }
  }, {
    // 禁用自动时间戳功能
    timestamps: false
  })

  return Expense
} 