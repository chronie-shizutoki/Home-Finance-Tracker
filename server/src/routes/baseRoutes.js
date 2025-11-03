/**
 * 基础路由配置
 * @module routes/baseRoutes
 * @desc 定义基础健康检查等通用API端点 - 适配全新架构
 */
const express = require('express')
const router = express.Router()
const { sequelize } = require('../db')
const fs = require('fs')
const path = require('path')
const os = require('os')

// 数据库连接状态检查函数
const checkDatabaseConnection = async () => {
  try {
    await sequelize.authenticate()
    return { status: 'connected', error: null }
  } catch (error) {
    return { status: 'disconnected', error: error.message }
  }
}

/**
 * @api {get} /api/health 系统健康检查
 * @apiName HealthCheck
 * @apiGroup Base
 * @apiSuccess {object} status 服务状态信息
 */
router.get('/api/health', async (req, res) => {
  // 设置CORS头
  res.header('Access-Control-Allow-Origin', '*')
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization')

  // 获取系统资源信息
  const memoryUsage = process.memoryUsage()
  const cpuInfo = os.cpus()
  
  // 检查关键目录和文件
  const serverDir = path.dirname(__dirname)
  const clientDistPath = path.join(__dirname, '../../../client/dist')
  const serverConfigPath = path.join(__dirname, '../../config/config.js')
  
  // 健康状态数据
  const healthData = {
    status: 'OK',
    timestamp: new Date().toISOString(),
    version: '2.0.0', // 更新版本号适配新架构
    uptime: process.uptime().toFixed(2) + 's',
    environment: {
      nodeVersion: process.version,
      nodeEnv: process.env.NODE_ENV || 'development',
      platform: os.platform(),
      arch: os.arch(),
      hostname: os.hostname()
    },
    resources: {
      memory: {
        rss: `${(memoryUsage.rss / 1024 / 1024).toFixed(2)} MB`,
        heapTotal: `${(memoryUsage.heapTotal / 1024 / 1024).toFixed(2)} MB`,
        heapUsed: `${(memoryUsage.heapUsed / 1024 / 1024).toFixed(2)} MB`,
        external: `${(memoryUsage.external / 1024 / 1024).toFixed(2)} MB`
      },
      cpu: {
        count: cpuInfo.length,
        model: cpuInfo[0]?.model || 'Unknown'
      },
      loadAverage: os.loadavg().map(avg => avg.toFixed(2))
    },
    services: {
      database: await checkDatabaseConnection(),
      fileSystem: {
        serverDirExists: fs.existsSync(serverDir),
        clientDistExists: fs.existsSync(clientDistPath),
        configExists: fs.existsSync(serverConfigPath)
      }
    },
    paths: {
      serverDir,
      clientDistPath,
      serverConfigPath
    }
  }

  res.status(200).json(healthData)
})

/**
 * @api {get} /api/health/lite 轻量级健康检查
 * @apiName LiteHealthCheck
 * @apiGroup Base
 * @apiSuccess {object} status 轻量服务状态信息
 */
router.get('/api/health/lite', async (req, res) => {
  res.header('Access-Control-Allow-Origin', '*')
  const dbStatus = await checkDatabaseConnection()
  res.status(200).json({
    status: 'OK',
    timestamp: new Date().toISOString(),
    database: dbStatus.status
  })
})

// 保持旧端点兼容性
router.get('/api', (req, res) => {
  res.redirect('/api/health')
})

module.exports = router
