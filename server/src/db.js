const Sequelize = require('sequelize')
const path = require('path')
const sequelize = new Sequelize({ dialect: 'sqlite', storage: path.join(__dirname, '../database.sqlite') })

// 导入现有模型
const Expense = require('./models/expense')(sequelize)
const Debt = require('./models/debt')(sequelize)

// 导入会员相关模型
const Member = require('./models/member')(sequelize)
const SubscriptionPlan = require('./models/subscriptionPlan')(sequelize)
const UserSubscription = require('./models/userSubscription')(sequelize)

// 导入日志相关函数
const { initLogTable, saveLog, getLogs, getLogsCount, cleanOldLogs } = require('./models/log')

// 定义模型关系
Member.hasMany(UserSubscription, { foreignKey: 'memberId' })
SubscriptionPlan.hasMany(UserSubscription, { foreignKey: 'planId' })
UserSubscription.belongsTo(Member, { foreignKey: 'memberId' })
UserSubscription.belongsTo(SubscriptionPlan, { foreignKey: 'planId' })

const syncDatabase = async () => {
  try {
    await sequelize.sync({ force: false })
    console.log('Database synchronized successfully.')
  } catch (error) {
    console.error('Unable to synchronize the database:', error)
    throw error
  }
}

module.exports = {
  sequelize,
  Expense,
  Debt,
  Member,
  SubscriptionPlan,
  UserSubscription,
  syncDatabase,
  initLogTable,
  saveLog,
  getLogs,
  getLogsCount,
  cleanOldLogs
}
