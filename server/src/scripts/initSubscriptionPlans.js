const { SubscriptionPlan, sequelize } = require('../db')

/**
 * 初始化默认的订阅计划
 * 包括月度、季度和年度三种订阅选项
 */
const initSubscriptionPlans = async () => {
  try {
    console.log('开始初始化订阅计划...')
    
    // 默认订阅计划配置
    const defaultPlans = [
      {
        name: '月度会员',
        description: '享受30天的高级会员服务',
        duration: 30,
        price: 199.99,
        period: 'monthly',
        isActive: true
      },
      {
        name: '季度会员',
        description: '享受90天的高级会员服务，比月度更优惠',
        duration: 90,
        price: 499.99,
        period: 'quarterly',
        isActive: true
      },
      {
        name: '年度会员',
        description: '享受365天的高级会员服务，最划算的选择',
        duration: 365,
        price: 1999.99,
        period: 'yearly',
        isActive: true
      }
    ]
    
    // 批量创建或更新订阅计划
    for (const planConfig of defaultPlans) {
      const [plan, created] = await SubscriptionPlan.findOrCreate({
        where: { period: planConfig.period },
        defaults: planConfig
      })
      
      if (created) {
        console.log(`创建了新的订阅计划: ${plan.name}`)
      } else {
        // 如果计划已存在，则更新信息
        await plan.update(planConfig)
        console.log(`更新了订阅计划: ${plan.name}`)
      }
    }
    
    console.log('订阅计划初始化完成！')
  } catch (error) {
    console.error('初始化订阅计划失败:', error)
    throw error
  }
}

if (require.main === module) {
  // 如果直接运行此脚本
  initSubscriptionPlans().catch(console.error)
}

module.exports = initSubscriptionPlans