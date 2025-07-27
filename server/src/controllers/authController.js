const path = require('path')
const result = require('dotenv').config({ path: path.resolve(__dirname, '../../.env') })
if (result.error) {
  throw new Error(`Failed to load .env file: ${result.error.message}`)
}
if (!process.env.JWT_SECRET) {
  throw new Error('JWT_SECRET is not defined in environment variables')
}
// 添加JWT_SECRET检测日志
console.log('[authController.js] 当前JWT_SECRET:', process.env.JWT_SECRET)
const speakeasy = require('speakeasy')
const jwt = require('jsonwebtoken')
const bcrypt = require('bcrypt')
const { User } = require('../db')
const apiResponse = require('../utils/apiResponse')
const authErrors = require('../utils/authErrors')
const { v4: uuidv4 } = require('uuid')

// 临时存储待验证的注册用户
const pendingRegistrations = new Map()

/**
 * 处理2FA登录验证
 * @route POST /api/auth/login
 * @access Public
 */
module.exports.loginWith2FA = async (req, res) => {
  try {
    const { account, code } = req.body

    // 验证请求参数
    if (!account || !code) {
      return apiResponse.error(res, '账号和验证码不能为空', 400)
    }

    // 查询用户（实际应用中应从数据库查询）
    const user = await User.findOne({ where: { username: account } })
    if (!user) {
      return apiResponse.error(res, '用户不存在', 401)
    }

    // 验证TOTP代码
    const verified = speakeasy.totp.verify({
      secret: user.totpSecret,
      encoding: 'base32',
      token: code
    })

    if (!verified) {
      return apiResponse.error(res, '验证码无效或已过期', 401)
    }

    // 验证成功，返回认证状态
    console.log('JWT_SECRET defined:', !!process.env.JWT_SECRET)
    console.log('JWT_SECRET length:', process.env.JWT_SECRET?.length)
    return apiResponse.success(res, {
      userId: user.id,
      account: user.account,
      token: jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: '60d' })
    })
  } catch (error) {
    console.error('登录验证错误:', error)
    return apiResponse.error(res, `服务器内部错误: ${error.message}`, 500)
  }
}

/**
 * 生成TOTP密钥（用于用户注册或2FA启用流程）
 * @route POST /api/auth/generate-totp
 * @access Private
 */
/**
 * 处理用户注册请求，生成TOTP密钥
 * @route POST /api/auth/register
 * @access Public
 */
module.exports.register = async (req, res) => {
  try {
    const { username } = req.body

    if (!username) {
      return apiResponse.error(res, '用户名不能为空', 400)
    }

    // 检查用户名是否已存在
    const existingUser = await User.findOne({ where: { username } })
    if (existingUser) {
      return apiResponse.error(res, '用户名已存在', 409)
    }

    // 生成临时ID和TOTP密钥
    const tempId = uuidv4()
    const secret = speakeasy.generateSecret({
      name: `HomeMoney:${encodeURIComponent(username)}`,
      issuer: 'HomeMoney'
    })

    // 存储临时注册信息（有效期10分钟）
    pendingRegistrations.set(tempId, {
      username,
      secret: secret.base32,
      expiresAt: Date.now() + 10 * 60 * 1000
    })

    return apiResponse.success(res, {
      tempId,
      secret: secret.base32,
      otpauth_url: secret.otpauth_url
    })
  } catch (error) {
    console.error('注册请求错误:', error)
    return apiResponse.error(res, `服务器内部错误: ${error.message}`, 500)
  }
}

/**
 * 验证注册验证码并完成用户创建
 * @route POST /api/auth/verify-register
 * @access Public
 */
module.exports.verifyRegister = async (req, res) => {
  try {
    const { tempId, code } = req.body

    if (!tempId || !code) {
      return apiResponse.error(res, '临时ID和验证码不能为空', 400)
    }

    // 获取临时注册信息
    const registration = pendingRegistrations.get(tempId)
    if (!registration) {
      return apiResponse.error(res, '注册信息不存在或已过期', 404)
    }

    // 检查是否过期
    if (Date.now() > registration.expiresAt) {
      pendingRegistrations.delete(tempId)
      return apiResponse.error(res, '注册链接已过期，请重新注册', 400)
    }

    // 验证TOTP代码
    const verified = speakeasy.totp.verify({
      secret: registration.secret,
      encoding: 'base32',
      token: code
    })

    if (!verified) {
      return apiResponse.error(res, '验证码无效或已过期', 401)
    }

    // 创建新用户
    // 生成默认密码哈希（实际应用中应要求用户设置密码）
    const defaultPassword = 'Dn+5J"+/!=.Y*"$h/f%#Ea:fs6z*JHAhmr2F*PFvw/"y=)R%sKRC+A.>{uNS_]"rJt;srj"n-/<2_;hH6&N?+PN[se}t&<2{_U:k'
    const passwordHash = bcrypt.hashSync(defaultPassword, 10)

    const newUser = await User.create({
      username: registration.username,
      passwordHash,
      totpSecret: registration.secret,
      isAdmin: false
    })

    // 清除临时注册信息
    pendingRegistrations.delete(tempId)

    return apiResponse.success(res, '注册成功', {
      userId: newUser.id,
      username: newUser.username
    }, 200)
  } catch (error) {
    console.error('注册验证错误:', error)
    return apiResponse.error(res, `服务器内部错误: ${error.message}`, 500)
  }
}

module.exports.generateTOTPSecret = async (req, res) => {
  try {
    const { userId } = req.body
    if (!userId) {
      return apiResponse.error(res, '用户ID不能为空', 400)
    }
    const user = await User.findByPk(userId)

    if (!user) {
      return apiResponse.error(res, '用户不存在', 404)
    }

    // 生成TOTP密钥
    const secret = speakeasy.generateSecret({
      name: `HomeMoney:${user.account}`
    })

    // 保存密钥到用户记录（实际应用中应加密存储）
    user.totpSecret = secret.base32
    await user.save()

    return apiResponse.success(res, {
      secret: secret.base32,
      otpauth_url: secret.otpauth_url
    })
  } catch (error) {
    console.error('生成TOTP密钥错误:', error)
    return apiResponse.error(res, '服务器内部错误', 500)
  }
}

/**
 * 用户登出接口
 * @route POST /api/auth/logout
 * @access Private
 */
module.exports.getUserInfo = async (req, res) => {
  try {
    // 验证用户是否已通过认证
    if (!req.userId) {
      console.error('获取用户信息失败: req.userId 未定义')
      return apiResponse.error(res, authErrors.userNotLoggedIn, 401)
    }

    // 验证用户ID格式
    if (typeof req.userId !== 'number' && typeof req.userId !== 'string') {
      console.error('获取用户信息失败: 用户ID格式无效', req.userId)
      return apiResponse.error(res, authErrors.invalidUserIdFormat, 400)
    }
    // 从认证中间件获取用户ID并查询对应用户
    let user = await User.findOne({
      where: { id: req.userId },
      attributes: ['id', 'username', 'totpSecret']
    })
    console.log('getUserInfo query result:', user)

    // 如果没有找到用户，创建一个默认用户
    if (!user) {
      const defaultPassword = 'Dn+5J"+/!=.Y*"$h/f%#Ea:fs6z*JHAhmr2F*PFvw/"y=)R%sKRC+A.>{uNS_]"rJt;srj"n-/<2_;hH6&N?+PN[se}t&<2{_U:k' // 生产环境中应移除或修改
      const passwordHash = bcrypt.hashSync(defaultPassword, 10)
      const secret = speakeasy.generateSecret({
        name: 'HomeMoney:defaultUser',
        issuer: 'HomeMoney'
      })

      user = await User.create({
        username: 'defaultUser',
        passwordHash,
        totpSecret: secret.base32,
        isAdmin: false
      })
      console.log('Created default user:', user)
    }

    return apiResponse.success(res, user)
  } catch (error) {
    console.error('获取用户信息错误:', error)
    return apiResponse.error(res, `服务器内部错误: ${error.message}`, 500)
  }
}

module.exports.logout = async (req, res) => {
  try {
    const { target } = req.query

    if (!target) {
      return apiResponse.error(res, 'Target URL is required', 400)
    }

    const decodedTarget = decodeURIComponent(target)

    if (!decodedTarget) {
      return apiResponse.error(res, 'Target URL is required', 400)
    }

    // 实际应用中应添加令牌失效逻辑
    return apiResponse.success(res, { redirectUrl: decodedTarget })
  } catch (error) {
    console.error('Logout error:', error)
    return apiResponse.error(res, `服务器内部错误: ${error.message}`, 500)
  }
}
