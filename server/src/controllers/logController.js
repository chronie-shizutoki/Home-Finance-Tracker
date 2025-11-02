/**
 * 日志控制器
 * @module controllers/logController
 * @desc 处理日志相关的HTTP请求
 */

const { saveLog, getLogs, getLogsCount, cleanOldLogs, sequelize } = require('../db');
const { success, error } = require('../utils/apiResponse');

/**
 * 接收并保存前端发送的日志
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 */
const receiveLog = async (req, res) => {
  try {
    const logData = req.body;
    
    // 验证请求体是否存在
    if (!logData) {
      return error(res, '请求体不能为空', 400);
    }
    
    // 验证必要字段
    if (!logData.timestamp || !logData.type) {
      return error(res, '参数错误', 400);
    }
    
    // 保存日志（异步处理，不阻塞响应）
    saveLog(logData, sequelize).catch(err => {
      console.error('异步保存日志失败:', err);
      // 不抛出错误，避免影响主流程
    });
    
    // 立即返回成功响应，不等待保存完成
    return success(res, { message: '日志接收成功' });
  } catch (err) {
    console.error('接收日志失败:', err);
    // 即使处理失败也返回成功状态，因为日志不是核心业务
    return success(res, { message: '日志已接收' });
  }
};

/**
 * 获取日志列表（管理员功能）
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 */
const getLogsList = async (req, res) => {
  try {
    // 这里可以添加管理员权限验证
    
    const { 
      limit = 100, 
      offset = 0, 
      type, 
      startDate, 
      endDate, 
      username 
    } = req.query;
    
    const query = {
      limit: Number(limit),
      offset: Number(offset),
      type,
      startDate,
      endDate,
      username
    };
    
    const [logs, total] = await Promise.all([
      getLogs(query, sequelize),
      getLogsCount(query, sequelize)
    ]);
    
    return success(res, '获取日志列表成功', {
      logs,
      total,
      page: Math.floor(offset / limit) + 1,
      pageSize: Number(limit),
      totalPages: Math.ceil(total / limit)
    });
  } catch (err) {
    console.error('获取日志列表失败:', err);
    return error(res, '服务器内部错误', 500);
  }
};

/**
 * 清理过期日志（管理员功能）
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 */
const cleanLogs = async (req, res) => {
  try {
    // 这里可以添加管理员权限验证
    
    const { days = 90 } = req.query;
    const daysToKeep = Number(days);
    
    if (daysToKeep < 1) {
      return error(res, '清理日志参数错误', 400);
    }
    
    // 计算清理日期
    const cleanDate = new Date();
    cleanDate.setDate(cleanDate.getDate() - daysToKeep);
    const cleanDateStr = cleanDate.toISOString();
    
    const result = await cleanOldLogs(cleanDateStr, sequelize);
    
    return success(res, '清理日志成功', {
      message: `成功清理${result.deletedCount}条过期日志`,
      deletedCount: result.deletedCount,
      keptDays: daysToKeep
    });
  } catch (err) {
    console.error('清理日志失败:', err);
    return error(res, '清理日志失败', 500, err);
  }
};

/**
 * 统计日志信息（管理员功能）
 * @param {Object} req - Express请求对象
 * @param {Object} res - Express响应对象
 */
const getLogStats = async (req, res) => {
  try {
    // 这里可以添加管理员权限验证
    
    const { startDate, endDate } = req.query;
    
    // 统计不同类型的日志数量
    const types = ['user_action', 'api_request', 'api_response', 'api_error', 'page_error', 'performance'];
    const statsPromises = types.map(async (type) => {
      const count = await getLogsCount({
        type,
        startDate,
        endDate
      }, sequelize);
      return { type, count };
    });
    
    const typeStats = await Promise.all(statsPromises);
    const total = await getLogsCount({ startDate, endDate }, sequelize);
    
    return success(res, '获取日志统计成功', {
      total,
      typeStats,
      period: {
        start: startDate,
        end: endDate
      }
    });
  } catch (err) {
    console.error('获取日志统计失败:', err);
    return error(res, '获取日志统计失败', 500, err);
  }
};

module.exports = {
  receiveLog,
  getLogsList,
  cleanLogs,
  getLogStats
};