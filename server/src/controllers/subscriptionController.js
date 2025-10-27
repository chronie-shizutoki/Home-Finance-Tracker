const { SubscriptionPlan, UserSubscription, Member, sequelize } = require('../db')
const { Op } = require('sequelize')
const dayjs = require('dayjs')

// 获取所有可用的订阅计划
const getSubscriptionPlans = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法获取订阅计划:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    
    const plans = await SubscriptionPlan.findAll({
      where: { isActive: true },
      order: [['price', 'ASC']]
    })
    
    res.json({
      success: true,
      data: plans
    })
  } catch (error) {
    console.error('获取订阅计划失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 创建用户订阅
const createSubscription = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法创建订阅:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username, planId, paymentId, autoRenew = false } = req.body
    
    if (!username || !planId || !paymentId) {
      return res.status(400).json({ error: '缺少必要参数' })
    }
    
    // 查找会员
    const member = await Member.findOne({ where: { username } })
    if (!member) {
      return res.status(404).json({ error: '会员不存在' })
    }
    
    // 查找订阅计划
    const plan = await SubscriptionPlan.findOne({ where: { period: planId } })
    if (!plan || !plan.isActive) {
      return res.status(404).json({ error: '订阅计划不存在或已停用' })
    }
    
    // 计算订阅结束日期
    const startDate = new Date()
    const endDate = dayjs(startDate).add(plan.duration, 'day').toDate()
    
    // 创建订阅记录
    const subscription = await UserSubscription.create({
      memberId: member.id,
      planId: plan.id, // 使用订阅计划的实际ID
      startDate,
      endDate,
      paymentId,
      autoRenew,
      status: 'active'
    })
    
    // 激活会员
    await member.update({ isActive: true })
    
    res.json({
      success: true,
      data: subscription
    })
  } catch (error) {
    console.error('创建订阅失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 获取用户当前订阅
const getCurrentSubscription = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法获取当前订阅:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username } = req.params
    
    const member = await Member.findOne({ where: { username } })
    if (!member) {
      return res.status(404).json({ error: '会员不存在' })
    }
    
    // 获取所有活跃的订阅记录
    const now = new Date()
    const subscriptions = await UserSubscription.findAll({
      where: {
        memberId: member.id,
        status: 'active',
        [Op.or]: [
          // 正常情况：日期范围有效
          {
            startDate: { [Op.lte]: now },
            endDate: { [Op.gte]: now }
          },
          // 特殊情况：startDate或endDate为空但状态为active
          {
            [Op.or]: [
              { startDate: null },
              { startDate: '' },
              { endDate: null },
              { endDate: '' }
            ]
          }
        ]
      },
      include: [SubscriptionPlan]
    })
    
    // 如果有多个订阅，选择到期日期最远的那个
    let currentSubscription = null
    if (subscriptions.length > 0) {
      // 先过滤出有有效endDate的订阅
      const subscriptionsWithEndDate = subscriptions.filter(sub => sub.endDate)
      
      if (subscriptionsWithEndDate.length > 0) {
        // 按endDate降序排序，选择最远的一个
        currentSubscription = subscriptionsWithEndDate.sort((a, b) => 
          new Date(b.endDate) - new Date(a.endDate)
        )[0]
      } else {
        // 如果都没有endDate，选择第一个
        currentSubscription = subscriptions[0]
      }
      
      // 同时返回所有订阅信息，方便前端处理多个订阅的情况
      currentSubscription.allSubscriptions = subscriptions
    }
    
    res.json({
      success: true,
      data: currentSubscription || null,
      totalActiveSubscriptions: subscriptions.length
    })
  } catch (error) {
    console.error('获取当前订阅失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 取消订阅
const cancelSubscription = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法取消订阅:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username } = req.params
    
    const member = await Member.findOne({ where: { username } })
    if (!member) {
      return res.status(404).json({ error: '会员不存在' })
    }
    
    const now = new Date()
    const currentSubscription = await UserSubscription.findOne({
      where: {
        memberId: member.id,
        status: 'active',
        startDate: { [Op.lte]: now },
        endDate: { [Op.gte]: now }
      }
    })
    
    if (!currentSubscription) {
      return res.status(404).json({ error: '没有活跃的订阅' })
    }
    
    // 只取消自动续费，不立即终止订阅
    await currentSubscription.update({ 
      autoRenew: false,
      status: 'canceled',
      endDate: now // 立即终止订阅
    })
    
    res.json({
      success: true,
      message: '订阅已成功取消',
      data: currentSubscription
    })
  } catch (error) {
    console.error('取消订阅失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 检查并更新过期订阅
const updateExpiredSubscriptions = async () => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，跳过更新过期订阅:', connectionError.message)
      return
    }
    
    const now = new Date()
    
    // 查找所有已过期但状态仍为active的订阅
    const expiredSubscriptions = await UserSubscription.findAll({
      where: {
        status: 'active',
        endDate: { [Op.lt]: now }
      },
      include: [Member]
    })
    
    for (const subscription of expiredSubscriptions) {
      // 更新订阅状态为过期
      await subscription.update({ status: 'expired' })
      
      // 检查该用户是否还有其他活跃订阅
      const hasActiveSubscription = await UserSubscription.findOne({
        where: {
          memberId: subscription.memberId,
          id: { [Op.ne]: subscription.id },
          status: 'active',
          startDate: { [Op.lte]: now },
          endDate: { [Op.gte]: now }
        }
      })
      
      // 如果没有其他活跃订阅，将会员状态设为非活跃
      if (!hasActiveSubscription) {
        await subscription.Member.update({ isActive: false })
      }
    }
    
    console.log(`已处理 ${expiredSubscriptions.length} 个过期订阅`)
  } catch (error) {
    console.error('更新过期订阅失败:', error)
  }
}

module.exports = {
  getSubscriptionPlans,
  createSubscription,
  getCurrentSubscription,
  cancelSubscription,
  updateExpiredSubscriptions
}