const express = require('express')
const { getConnectionStatus } = require('../db')
const router = express.Router()

// 导入日志路由
const logRoutes = require('./logRoutes')

// 使用日志路由
router.use('/logs', logRoutes)

module.exports = router
