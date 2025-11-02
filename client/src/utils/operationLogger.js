/**
 * 操作日志记录工具
 * @module utils/operationLogger
 * @desc 收集和上报用户操作日志，包括用户行为、设备信息和请求响应数据
 */

/**
 * 获取用户设备信息
 * @returns {Object} 设备信息对象
 */
function getDeviceInfo() {
  return {
    userAgent: navigator.userAgent,
    language: navigator.language || navigator.userLanguage,
    platform: navigator.platform,
    screen: {
      width: window.screen.width,
      height: window.screen.height,
      colorDepth: window.screen.colorDepth
    },
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    connection: navigator.connection ? {
      effectiveType: navigator.connection.effectiveType,
      rtt: navigator.connection.rtt,
      downlink: navigator.connection.downlink
    } : null
  };
}

/**
 * 获取用户标识信息
 * @returns {Object} 用户标识对象
 */
function getUserInfo() {
  try {
    // 从localStorage获取用户信息
    const username = localStorage.getItem('username') || 'guest';
    const userId = localStorage.getItem('userId') || 'unknown';
    
    return {
      username,
      userId,
      sessionId: sessionStorage.getItem('sessionId') || createSessionId()
    };
  } catch (error) {
    console.error('获取用户信息失败:', error);
    return {
      username: 'guest',
      userId: 'unknown',
      sessionId: createSessionId()
    };
  }
}

/**
 * 创建会话ID
 * @returns {string} 唯一的会话ID
 */
function createSessionId() {
  const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  sessionStorage.setItem('sessionId', sessionId);
  return sessionId;
}

/**
 * 格式化日志数据
 * @param {Object} logData - 原始日志数据
 * @returns {Object} 格式化后的日志数据
 */
function formatLogData(logData) {
  return {
    timestamp: new Date().toISOString(),
    ...logData,
    device: getDeviceInfo(),
    user: getUserInfo(),
    page: {
      url: window.location.href,
      referrer: document.referrer,
      title: document.title
    }
  };
}

/**
 * 上报日志到服务器
 * @param {Object} logData - 日志数据
 */
async function reportLog(logData) {
  try {
    const formattedLog = formatLogData(logData);
    
    // 使用navigator.sendBeacon API发送日志（更可靠，不阻塞页面）
    let isBeaconSupported = false;
    if (navigator.sendBeacon) {
      try {
        const blob = new Blob([JSON.stringify(formattedLog)], { type: 'application/json' });
        isBeaconSupported = navigator.sendBeacon('/api/logs', blob);
      } catch (beaconError) {
        console.warn('Beacon API使用失败，将使用fetch:', beaconError);
      }
    }
    
    if (!isBeaconSupported) {
      // fallback到fetch API
      await fetch('/api/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(formattedLog),
        // 不阻止页面卸载
        keepalive: true
      });
    }
  } catch (error) {
    console.error('日志上报失败:', error);
    // 考虑将失败的日志存储到localStorage，稍后重试
    try {
      const failedLogs = JSON.parse(localStorage.getItem('failedLogs') || '[]');
      failedLogs.push({
        timestamp: new Date().toISOString(),
        data: logData
      });
      // 只保留最近100条失败的日志
      if (failedLogs.length > 100) {
        failedLogs.splice(0, failedLogs.length - 100);
      }
      localStorage.setItem('failedLogs', JSON.stringify(failedLogs));
    } catch (e) {
      console.error('存储失败日志失败:', e);
    }
  }
}

/**
 * 尝试重新上报失败的日志
 */
async function retryFailedLogs() {
  try {
    const failedLogs = JSON.parse(localStorage.getItem('failedLogs') || '[]');
    if (failedLogs.length === 0) return;
    
    const logsToRetry = [...failedLogs];
    localStorage.removeItem('failedLogs');
    
    for (const logItem of logsToRetry) {
      await reportLog(logItem.data);
    }
  } catch (error) {
    console.error('重试失败日志失败:', error);
  }
}

/**
 * 记录用户行为日志
 * @param {string} action - 操作名称
 * @param {Object} details - 操作详情
 */
export function logUserAction(action, details = {}) {
  reportLog({
    type: 'user_action',
    action,
    details
  });
}

/**
 * 记录API请求日志
 * @param {Object} config - Axios请求配置
 */
/**
 * 安全过滤请求体，移除敏感信息
 * @param {any} data - 请求数据
 * @returns {any} 过滤后的数据
 */
function sanitizeRequestBody(data) {
  if (!data || typeof data !== 'object') {
    return data;
  }
  
  const sensitiveKeys = ['password', 'token', 'auth', 'creditCard', 'cardNumber', 'cvv'];
  const sanitized = { ...data };
  
  // 过滤敏感字段
  sensitiveKeys.forEach(key => {
    if (sanitized[key]) {
      sanitized[key] = '[PROTECTED]';
    }
  });
  
  return sanitized;
}

export function logApiRequest(config) {
  // 避免记录敏感信息
  const sanitizedConfig = {
    method: config.method,
    url: config.url,
    params: config.params,
    timestamp: Date.now()
  };
  
  // 安全记录请求体，移除敏感信息
  if (config.data) {
    try {
      // 尝试序列化数据来检查大小
      const dataStr = typeof config.data === 'string' ? config.data : JSON.stringify(config.data);
      if (dataStr.length <= 5 * 1024) { // 限制在5KB以内
        sanitizedConfig.body = sanitizeRequestBody(config.data);
      } else {
        // 对于大请求体，仍然只记录基本信息
        sanitizedConfig.hasBody = true;
        sanitizedConfig.bodyType = typeof config.data;
        sanitizedConfig.bodySize = dataStr.length;
      }
    } catch (e) {
      // 如果序列化失败，只记录基本信息
      sanitizedConfig.hasBody = true;
      sanitizedConfig.bodyType = typeof config.data;
    }
  }
  
  // 存储请求ID，用于匹配响应
  const requestId = `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  config._requestId = requestId;
  
  reportLog({
    type: 'api_request',
    requestId,
    request: sanitizedConfig
  });
}

/**
 * 记录API响应日志
 * @param {Object} response - Axios响应对象
 */
/**
 * 安全过滤响应数据，移除敏感信息
 * @param {any} data - 响应数据
 * @returns {any} 过滤后的数据
 */
function sanitizeResponseBody(data) {
  if (!data || typeof data !== 'object') {
    return data;
  }
  
  const sensitiveKeys = ['password', 'token', 'auth', 'creditCard', 'cardNumber', 'cvv'];
  
  // 处理数组
  if (Array.isArray(data)) {
    return data.map(item => sanitizeResponseBody(item));
  }
  
  // 处理对象
  const sanitized = { ...data };
  
  // 过滤敏感字段
  sensitiveKeys.forEach(key => {
    if (sanitized[key]) {
      sanitized[key] = '[PROTECTED]';
    }
  });
  
  return sanitized;
}

export function logApiResponse(response) {
  const requestId = response.config._requestId;
  
  // 记录更详细的请求信息
  const requestInfo = {
    method: response.config.method,
    url: response.config.url,
    params: response.config.params,
    startTime: response.config.timestamp
  };
  
  // 限制响应数据大小并过滤敏感信息
  let responseData;
  try {
    const dataStr = JSON.stringify(response.data);
    
    // 对于中小型响应数据，进行安全过滤后完整记录
    if (dataStr.length <= 8 * 1024) { // 限制在8KB以内
      responseData = sanitizeResponseBody(response.data);
    } else {
      // 对于大型响应数据，保留部分信息但增加更多细节
      responseData = {
        truncated: true,
        size: dataStr.length,
        type: Array.isArray(response.data) ? 'array' : typeof response.data,
        keys: typeof response.data === 'object' ? Object.keys(response.data) : undefined,
        // 对于数组，记录元素数量
        itemCount: Array.isArray(response.data) ? response.data.length : undefined,
        // 对于对象，记录第一层属性数量
        propertyCount: typeof response.data === 'object' ? Object.keys(response.data).length : undefined
      };
    }
  } catch (error) {
    responseData = { 
      error: '无法序列化响应数据',
      originalType: typeof response.data
    };
  }
  
  reportLog({
    type: 'api_response',
    requestId,
    request: requestInfo, // 包含请求信息，让日志更完整
    response: {
      status: response.status,
      statusText: response.statusText,
      data: responseData,
      headers: Object.fromEntries(Object.entries(response.headers || {}).filter(
        ([key]) => !['authorization', 'cookie'].includes(key.toLowerCase())
      ))
    },
    duration: Date.now() - (response.config.timestamp || Date.now()),
    timestamp: new Date().toISOString() // 添加完整的时间戳
  });
}

/**
 * 记录API错误日志
 * @param {Object} error - Axios错误对象
 */
export function logApiError(error) {
  const requestId = error.config?._requestId;
  
  reportLog({
    type: 'api_error',
    requestId,
    error: {
      message: error.message,
      code: error.code,
      status: error.response?.status,
      config: error.config ? {
        method: error.config.method,
        url: error.config.url,
        hasBody: !!error.config.data
      } : undefined
    }
  });
}

/**
 * 记录页面错误
 * @param {Error} error - 错误对象
 * @param {string} source - 错误来源
 */
export function logPageError(error, source = 'global') {
  reportLog({
    type: 'page_error',
    error: {
      message: error.message,
      stack: error.stack,
      source
    }
  });
}

/**
 * 记录性能指标
 * @param {string} metricName - 指标名称
 * @param {number} value - 指标值
 * @param {Object} context - 上下文信息
 */
export function logPerformanceMetric(metricName, value, context = {}) {
  reportLog({
    type: 'performance',
    metric: metricName,
    value,
    context
  });
}

/**
 * 初始化全局错误监听
 */
export function initGlobalErrorMonitoring() {
  // 监听未捕获的JavaScript错误
  window.addEventListener('error', (event) => {
    logPageError(new Error(event.message), `line ${event.lineno}, col ${event.colno}, ${event.filename}`);
  });
  
  // 监听未处理的Promise拒绝
  window.addEventListener('unhandledrejection', (event) => {
    logPageError(
      event.reason || new Error('Promise rejection'), 
      'unhandledrejection'
    );
  });
}

/**
 * 尝试上报失败的日志
 */
export function tryReportFailedLogs() {
  retryFailedLogs();
}

/**
 * 初始化控制台日志捕获
 * @param {Object} options - 配置选项
 * @param {Array<string>} options.levels - 要捕获的日志级别，默认为['log', 'error', 'warn', 'info']
 * @param {number} options.maxLength - 单个日志消息的最大长度，默认为5000
 */
export function initConsoleLogging(options = {}) {
  const {
    levels = ['log', 'error', 'warn', 'info'],
    maxLength = 5000
  } = options;

  // 存储原始console方法
  const originalConsole = {};

  levels.forEach(level => {
    if (typeof console[level] === 'function') {
      originalConsole[level] = console[level];
      
      // 重写console方法
      console[level] = function(...args) {
        // 调用原始方法，确保控制台正常显示
        originalConsole[level].apply(console, args);
        
        // 处理日志参数
        try {
          // 尝试序列化参数，处理不同类型的值
          const formattedArgs = args.map(arg => {
            try {
              if (arg instanceof Error) {
                return {
                  message: arg.message,
                  stack: arg.stack,
                  name: arg.name
                };
              }
              return typeof arg === 'object' ? JSON.stringify(arg) : String(arg);
            } catch (e) {
              return '[无法序列化的值]';
            }
          });
          
          // 限制日志长度
          let logMessage = formattedArgs.join(' ');
          if (logMessage.length > maxLength) {
            logMessage = logMessage.substring(0, maxLength) + '... [截断]';
          }
          
          // 上报控制台日志
          reportLog({
            type: 'console_log',
            level,
            message: logMessage,
            timestamp: new Date().toISOString()
          });
        } catch (e) {
          // 如果日志处理出错，使用原始console记录错误，但不上报
          originalConsole.error('控制台日志捕获失败:', e);
        }
      };
    }
  });
}

/**
 * 导出默认对象
 */
export default {
  logUserAction,
  logApiRequest,
  logApiResponse,
  logApiError,
  logPageError,
  logPerformanceMetric,
  initGlobalErrorMonitoring,
  tryReportFailedLogs,
  initConsoleLogging
};