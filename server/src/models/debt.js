const { DataTypes } = require('sequelize')

module.exports = (sequelize) => {
  const Debt = sequelize.define('Debt', {
    type: {
      type: DataTypes.STRING,
      allowNull: false,
      validate: {
        isIn: [['lend', 'borrow']]
      }
    },
    person: {
      type: DataTypes.STRING,
      allowNull: false,
      validate: {
        notEmpty: true
      }
    },
    amount: {
      type: DataTypes.FLOAT,
      allowNull: false,
      validate: {
        isFloat: true,
        min: 0.01
      }
    },
    date: {
      type: DataTypes.DATE,
      allowNull: false
    },
    isRepaid: {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: false
    },
    remark: {
      type: DataTypes.STRING,
      allowNull: true
    }
  })

  return Debt
}