const axios = require('axios')
const dayjs = require('dayjs')
const fs = require('fs')
const path = require('path')
const { SubscriptionPlan, sequelize } = require('../db')

/**
 * 支付控制器
 * @module controllers/paymentController
 * @desc 处理用户捐赠等支付请求
 */

/**
 * 处理用户捐赠请求
 * @function donate
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 * @returns {Promise<void>}
 */
const donate = async (req, res) => {
  try {
    const { username, amount } = req.body
    
    // 后端数据验证
    if (!username || !amount) {
      return res.status(400).json({ 
        success: false, 
        error: '用户名和金额是必填项'
      })
    }
    
    if (typeof amount !== 'number' || amount < 0.01) {
      return res.status(400).json({ 
        success: false, 
        error: '金额必须是大于或等于0.01的数字'
      })
    }
    
    // 验证金额最多两位小数
    if (amount.toString().includes('.') && amount.toString().split('.')[1].length > 2) {
      return res.status(400).json({ 
        success: false, 
        error: '金额最多只能有两位小数'
      })
    }
    
    // 第三方支付API的请求参数
    const paymentData = {
      username: username,
      amount: amount,
      thirdPartyId: process.env.THIRD_PARTY_ID || 'HomeMoney',
      thirdPartyName: process.env.THIRD_PARTY_NAME || '家庭财务管理应用',
      description: `
        Donation by user ${username}的捐赠
        金额 Amount：${amount}
        时间 Time：${dayjs().format('YYYY-MM-DD HH:mm:ss')}
        若有问题请联系我们：
        If you have any questions, please contact us: 
        https://wj.qq.com/s2/24109109/3572/
      `
    }
    
    try {
      // 实际环境中调用第三方支付API
      // 默认地址已配置为您指定的API地址
      const THIRD_PARTY_PAYMENT_API = process.env.THIRD_PARTY_PAYMENT_API || 'http://192.168.0.197:3200/api/third-party/payments';
      
      const response = await axios.post(THIRD_PARTY_PAYMENT_API, paymentData, {
        timeout: 30000 // 30秒超时
      })
      
      // 记录捐赠日志
      console.log(`用户${username}捐赠了${amount}元`)
      
      // 保存捐赠记录到JSON文件
      const donationRecord = {
        id: Date.now().toString(),
        username: username,
        amount: amount,
        timestamp: dayjs().format('YYYY-MM-DD HH:mm:ss'),
        orderId: response.data.orderId || `DONATE_${Date.now()}`,
        status: 'success'
      };
      
      // 保存捐赠记录函数
      const saveDonationRecord = (record) => {
        try {
          // 确保数据目录存在
          const dataDir = path.join(__dirname, '../../data');
          if (!fs.existsSync(dataDir)) {
            fs.mkdirSync(dataDir, { recursive: true });
          }
          
          // 捐赠记录文件路径
          const donationFilePath = path.join(dataDir, 'donation_records.json');
          
          // 将记录转换为JSON字符串并添加换行符
          const recordLine = JSON.stringify(record) + '\n';
          
          // 追加记录到文件，每个记录占一行
          fs.appendFileSync(donationFilePath, recordLine, 'utf8');
          console.log('捐赠记录已保存到文件:', donationFilePath);
        } catch (fileError) {
          console.error('保存捐赠记录失败:', fileError);
          // 文件保存失败不影响主流程
        };
      
      // 异步保存捐赠记录
      saveDonationRecord(donationRecord);
      
      // 返回第三方API的响应
      res.status(200).json({
        success: true,
        data: response.data
      })
    }
  } catch (apiError) {
      console.error('调用第三方支付API失败:', apiError)
      res.status(500).json({
        success: false,
        error: '支付处理失败，请稍后重试',
        details: apiError.message
      })
    }
  
  } catch (error) {
    console.error('处理捐赠请求失败:', error)
    res.status(500).json({
      success: false,
      error: '服务器内部错误'
    })
  }
}

/**
 * 处理会员订阅支付请求
 * @function subscribePayment
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 * @returns {Promise<void>}
 */
const subscribePayment = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法处理订阅支付:', connectionError.message)
      return res.status(503).json({ 
        success: false, 
        error: '数据库服务暂时不可用' 
      })
    }
    
    const { username, planId } = req.body
    
    // 后端数据验证
    if (!username || !planId) {
      return res.status(400).json({ 
        success: false, 
        error: '用户名和订阅计划ID是必填项'
      })
    }
    
    // 获取订阅计划信息 - 使用period字段查找而不是ID
    const plan = await SubscriptionPlan.findOne({ where: { period: planId } })
    if (!plan || !plan.isActive) {
      return res.status(404).json({ 
        success: false, 
        error: '订阅计划不存在或已停用'
      })
    }
    
    // 第三方支付API的请求参数
    const paymentData = {
      username: username,
      amount: plan.price,
      thirdPartyId: process.env.THIRD_PARTY_ID || 'HomeMoney',
      thirdPartyName: process.env.THIRD_PARTY_NAME || '家庭财务管理应用',
      description: `
        会员订阅支付 - ${plan.name}
        User ${username} Membership Subscription
        金额 Amount：${plan.price}
        订阅周期 Subscription Period：${plan.period === 'monthly' ? '月度' : plan.period === 'quarterly' ? '季度' : '年度'}
        时间 Time：${dayjs().format('YYYY-MM-DD HH:mm:ss')}
        订阅计划ID Plan ID：${planId}
      `
    }
    
    try {
      // 实际环境中调用第三方支付API
      const THIRD_PARTY_PAYMENT_API = process.env.THIRD_PARTY_PAYMENT_API || 'http://192.168.0.197:3200/api/third-party/payments';
      
      const response = await axios.post(THIRD_PARTY_PAYMENT_API, paymentData, {
        timeout: 30000 // 30秒超时
      })
      
      // 记录订阅支付日志
      console.log(`用户${username}订阅了${plan.name}，金额${plan.price}元`)
      
      // 返回第三方API的响应，包含订单ID供后续创建订阅使用
      res.status(200).json({
        success: true,
        data: {
          ...response.data,
          planId: plan.id,
          planName: plan.name,
          price: plan.price
        }
      })
      
    } catch (apiError) {
      console.error('调用第三方支付API失败:', apiError)
      res.status(500).json({
        success: false,
        error: '支付处理失败，请稍后重试',
        details: apiError.message
      })
    }
    
  } catch (error) {
    console.error('处理订阅支付请求失败:', error)
    res.status(500).json({
      success: false,
      error: '服务器内部错误'
    })
  }
}

module.exports = {
  donate,
  subscribePayment
}