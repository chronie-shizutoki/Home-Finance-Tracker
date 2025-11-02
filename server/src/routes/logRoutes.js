/**
 * 日志相关路由
 * @module routes/logRoutes
 * @desc 定义日志相关的API端点
 */

const express = require('express');
const router = express.Router();
const logController = require('../controllers/logController');

/**
 * 接收前端发送的操作日志
 * @name POST /api/logs
 * @function
 * @memberof module:routes/logRoutes
 * @inner
 * @param {Object} req.body - 日志数据
 */
router.post('/', logController.receiveLog);

/**
 * 获取日志列表（管理员功能）
 * @name GET /api/logs
 * @function
 * @memberof module:routes/logRoutes
 * @inner
 * @param {Object} req.query - 查询参数
 * @param {number} req.query.limit - 限制返回数量
 * @param {number} req.query.offset - 偏移量
 * @param {string} req.query.type - 日志类型
 * @param {string} req.query.startDate - 开始日期
 * @param {string} req.query.endDate - 结束日期
 * @param {string} req.query.username - 用户名
 */
router.get('/', logController.getLogsList);

/**
 * 获取日志统计信息（管理员功能）
 * @name GET /api/logs/stats
 * @function
 * @memberof module:routes/logRoutes
 * @inner
 * @param {Object} req.query - 查询参数
 * @param {string} req.query.startDate - 开始日期
 * @param {string} req.query.endDate - 结束日期
 */
router.get('/stats', logController.getLogStats);

/**
 * 清理过期日志（管理员功能）
 * @name DELETE /api/logs/clean
 * @function
 * @memberof module:routes/logRoutes
 * @inner
 * @param {Object} req.query - 查询参数
 * @param {number} req.query.days - 保留天数，默认90天
 */
router.delete('/clean', logController.cleanLogs);

module.exports = router;