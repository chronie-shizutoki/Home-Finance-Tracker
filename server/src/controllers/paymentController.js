const axios = require('axios')
const dayjs = require('dayjs')

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
    
    if (typeof amount !== 'number' || amount <= 0) {
      return res.status(400).json({ 
        success: false, 
        error: '金额必须是大于0的数字'
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
      thirdPartyId: process.env.THIRD_PARTY_ID || 'homeMoneyApp',
      thirdPartyName: process.env.THIRD_PARTY_NAME || '家庭财务管理应用',
      description: `用户${username}的捐赠`
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
      
      // 返回第三方API的响应
      res.status(200).json({
        success: true,
        data: response.data
      })
      
      /*
      // 开发环境下的临时解决方案（仅当第三方API不可用时使用）
      const mockResponse = {
        orderId: `DONATE_${Date.now()}`,
        status: 'success',
        message: '捐赠成功',
        timestamp: dayjs().format('YYYY-MM-DD HH:mm:ss')
      };
      
      res.status(200).json({
        success: true,
        data: mockResponse
      })
      */
      
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

module.exports = {
  donate
}