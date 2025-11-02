/**
 * 操作日志模型
 * @module models/log
 * @desc 定义操作日志的数据结构和数据库交互
 */

/**
 * 初始化日志表
 * 确保日志表存在，不存在则创建
 */
const initLogTable = async (sequelize) => {
  try {
    // 使用原始SQL创建表，避免Sequelize模型定义的复杂性
    await sequelize.query(`
      CREATE TABLE IF NOT EXISTS operation_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp TEXT NOT NULL,
        type TEXT NOT NULL,
        action TEXT,
        request_id TEXT,
        user_info TEXT,
        device_info TEXT,
        page_info TEXT,
        details TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
      
      -- 创建索引以提高查询性能
      CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON operation_logs(timestamp);
      CREATE INDEX IF NOT EXISTS idx_logs_type ON operation_logs(type);
      CREATE INDEX IF NOT EXISTS idx_logs_user_id ON operation_logs(user_info);
    `);
    console.log('操作日志表初始化完成');
  } catch (error) {
    console.error('初始化日志表失败:', error);
    throw error;
  }
};

/**
 * 保存操作日志
 * @param {Object} logData - 日志数据
 * @param {Object} sequelize - Sequelize实例
 * @returns {Promise<Object>} 保存结果
 */
const saveLog = async (logData, sequelize) => {
  try {
    // 调试信息
    console.log('Log type:', logData.type);
    
    // 直接从logData获取字段值
    const timestamp = logData.timestamp || new Date().toISOString();
    const type = logData.type;
    const action = logData.action || null;
    const requestId = logData.requestId || null;
    
    // 构建detailsToSave，根据不同日志类型提取关键信息
    let detailsToSave = {};
    
    // 根据日志类型处理不同的信息
    switch (type) {
      case 'api_request':
      case 'api_response':
      case 'api_error':
        // API相关日志处理
        if (logData.request) {
          detailsToSave = {
            ...detailsToSave,
            method: logData.request.method,
            url: logData.request.url,
            params: logData.request.params,
            body: logData.request.body,
            hasBody: logData.request.hasBody,
            bodyType: logData.request.bodyType,
            bodySize: logData.request.bodySize
          };
        }
        
        if (logData.response) {
          detailsToSave = {
            ...detailsToSave,
            status: logData.response.status,
            statusText: logData.response.statusText,
            responseData: logData.response.data,
            responseHeaders: logData.response.headers,
            truncated: logData.response.data?.truncated
          };
        }
        
        if (logData.error) {
          detailsToSave.error = logData.error;
        }
        
        if (logData.duration) {
          detailsToSave.duration = logData.duration;
        }
        break;
        
      case 'console_log':
        // 控制台日志特殊处理
        detailsToSave = {
          ...detailsToSave,
          level: logData.level,
          message: logData.message
        };
        break;
        
      case 'page_error':
      case 'user_action':
      case 'performance':
      default:
        // 其他类型日志的通用处理
        if (logData.error) {
          detailsToSave.error = logData.error;
        }
        
        if (logData.details) {
          detailsToSave = {
            ...detailsToSave,
            ...logData.details
          };
        }
        
        if (type === 'performance') {
          detailsToSave.metric = logData.metric;
          detailsToSave.value = logData.value;
          detailsToSave.context = logData.context;
        }
        break;
    }
    
    // 移除undefined值
    Object.keys(detailsToSave).forEach(key => {
      if (detailsToSave[key] === undefined) {
        delete detailsToSave[key];
      }
    });
    
    // 确保我们不会存储空对象
    if (Object.keys(detailsToSave).length === 0) {
      detailsToSave = null;
    }
    
    // 使用原始SQL插入数据
    const [result] = await sequelize.query(
      `INSERT INTO operation_logs 
       (timestamp, type, action, request_id, user_info, device_info, page_info, details) 
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      {
        replacements: [
          timestamp,
          type,
          action,
          requestId,
          logData.user ? JSON.stringify(logData.user) : null,
          logData.device ? JSON.stringify(logData.device) : null,
          logData.page ? JSON.stringify(logData.page) : null,
          detailsToSave ? JSON.stringify(detailsToSave) : null
        ],
        type: sequelize.QueryTypes.INSERT
      }
    );
    
    return { success: true, id: result };
  } catch (error) {
    console.error('保存日志失败:', error);
    // 即使保存失败也不抛出错误，避免影响主业务流程
    return { success: false, error: error.message };
  }
};

/**
 * 查询日志记录
 * @param {Object} query - 查询条件
 * @param {Object} sequelize - Sequelize实例
 * @returns {Promise<Array>} 日志记录列表
 */
const getLogs = async (query = {}, sequelize) => {
  try {
    const { limit = 100, offset = 0, type, startDate, endDate, username } = query;
    
    // 构建查询SQL
    let sql = 'SELECT * FROM operation_logs WHERE 1=1';
    const replacements = [];
    
    if (type) {
      sql += ' AND type = ?';
      replacements.push(type);
    }
    
    if (username) {
      sql += ' AND (user_info LIKE ? OR user_info LIKE ?)';
      replacements.push(`%"username":"${username}"%`);
      replacements.push(`%"email":"${username}"%`);
    }
    
    if (startDate) {
      sql += ' AND timestamp >= ?';
      replacements.push(startDate);
    }
    
    if (endDate) {
      sql += ' AND timestamp <= ?';
      replacements.push(endDate);
    }
    
    sql += ' ORDER BY timestamp DESC LIMIT ? OFFSET ?';
    replacements.push(Number(limit), Number(offset));
    
    // 执行查询
    const logs = await sequelize.query(sql, {
      replacements,
      type: sequelize.QueryTypes.SELECT
    });
    
    // 解析JSON字段
    return logs.map(log => ({
      ...log,
      user_info: log.user_info ? JSON.parse(log.user_info) : null,
      device_info: log.device_info ? JSON.parse(log.device_info) : null,
      page_info: log.page_info ? JSON.parse(log.page_info) : null,
      details: log.details ? JSON.parse(log.details) : null
    }));
  } catch (error) {
    console.error('查询日志失败:', error);
    throw error;
  }
};

/**
 * 获取日志总数
 * @param {Object} query - 查询条件
 * @param {Object} sequelize - Sequelize实例
 * @returns {Promise<number>} 日志总数
 */
const getLogsCount = async (query = {}, sequelize) => {
  try {
    const { type, startDate, endDate, username } = query;
    
    let sql = 'SELECT COUNT(*) as count FROM operation_logs WHERE 1=1';
    const replacements = [];
    
    if (username) {
      sql += ' AND (user_info LIKE ? OR user_info LIKE ?)';
      replacements.push(`%"username":"${username}"%`);
      replacements.push(`%"email":"${username}"%`);
    }
    
    if (type) {
      sql += ' AND type = ?';
      replacements.push(type);
    }
    
    if (startDate) {
      sql += ' AND timestamp >= ?';
      replacements.push(startDate);
    }
    
    if (endDate) {
      sql += ' AND timestamp <= ?';
      replacements.push(endDate);
    }
    
    const result = await sequelize.query(sql, {
      replacements,
      type: sequelize.QueryTypes.SELECT
    });
    
    return result[0].count;
  } catch (error) {
    console.error('获取日志数量失败:', error);
    return 0;
  }
};

/**
 * 清理过期日志
 * @param {string} beforeDate - 清理此日期之前的日志
 * @param {Object} sequelize - Sequelize实例
 * @returns {Promise<Object>} 清理结果
 */
const cleanOldLogs = async (beforeDate, sequelize) => {
  try {
    const result = await sequelize.query(
      'DELETE FROM operation_logs WHERE timestamp < ?',
      {
        replacements: [beforeDate],
        type: sequelize.QueryTypes.DELETE
      }
    );
    
    // Sequelize的DELETE查询返回受影响的行数作为数组的第一个元素
    return { success: true, deletedCount: result[0] };
  } catch (error) {
    console.error('清理日志失败:', error);
    throw error;
  }
};

module.exports = {
  initLogTable,
  saveLog,
  getLogs,
  getLogsCount,
  cleanOldLogs
};