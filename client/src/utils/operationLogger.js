/**
 * Operation Logger Utility
 * @module utils/operationLogger
 * @desc Collect and report user operation logs, including user behavior, device information, and request/response data.
 * 
 * Configuration:
 * - Only report ERROR and WARN levels of logs
 * - API logs only record errors and exceptions
 * - Reduce console log capture
 * - Optimize logging strategy
 */

// Log levels configuration
const LOG_LEVELS = {
  ERROR: 'error',
  WARN: 'warn',
  INFO: 'info',
  DEBUG: 'debug'
};

// Current log level - default ERROR to avoid excessive logging
let currentLogLevel = LOG_LEVELS.ERROR;

// Log reporting rate limit
let lastReportTime = 0;
const MIN_REPORT_INTERVAL = 5000; // Minimum 5 seconds interval

/**
 * Check if log reporting is allowed (rate limit)
 * @returns {boolean} Whether to report log
 */
function canReportLog() {
  const now = Date.now();
  const timeSinceLastReport = now - lastReportTime;
  
  if (timeSinceLastReport < MIN_REPORT_INTERVAL) {
    return false;
  }
  
  lastReportTime = now;
  return true;
}

/**
 * Set log level
 * @param {string} level - Log level
 */
export function setLogLevel(level) {
  if (Object.values(LOG_LEVELS).includes(level)) {
    currentLogLevel = level;
  }
}

/**
 * Check if this level of log should be recorded
 * @param {string} level - Log level
 * @returns {boolean} Whether to log
 */
function shouldLog(level) {
  const levelPriority = {
    [LOG_LEVELS.DEBUG]: 0,
    [LOG_LEVELS.INFO]: 1,
    [LOG_LEVELS.WARN]: 2,
    [LOG_LEVELS.ERROR]: 3
  };
  
  return levelPriority[level] >= levelPriority[currentLogLevel];
}

/**
 * Get user device information
 * @returns {Object} Device information object
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
 * Get user identity information
 * @returns {Object} User identity object
 */
function getUserInfo() {
  try {
    // Get user identity from localStorage
    const username = localStorage.getItem('username') || 'guest';
    const userId = localStorage.getItem('userId') || 'unknown';
    
    return {
      username,
      userId,
      sessionId: sessionStorage.getItem('sessionId') || createSessionId()
    };
  } catch (error) {
    console.error('Error fetching user identity:', error);
    return {
      username: 'guest',
      userId: 'unknown',
      sessionId: createSessionId()
    };
  }
}

/**
 * Create unique session ID
 * @returns {string} Unique session ID
 */
function createSessionId() {
  const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  sessionStorage.setItem('sessionId', sessionId);
  return sessionId;
}

/**
 * Format log data
 * @param {Object} logData - Original log data
 * @returns {Object} Formatted log data
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
 * Report log to server
 * @param {Object} logData - Log data
 * @param {string} level - Log level
 */
async function reportLog(logData, level = LOG_LEVELS.ERROR) {
  // Check if this level of log should be recorded
  if (!shouldLog(level)) {
    return;
  }
  
  // Rate limit check: check if log reporting is allowed
  if (!canReportLog()) {
    return;
  }
  
  try {
    const formattedLog = formatLogData(logData);
    formattedLog.level = level;
    
    // Use navigator.sendBeacon API to send log (more reliable, non-blocking)
    let isBeaconSupported = false;
    if (navigator.sendBeacon) {
      try {
        const blob = new Blob([JSON.stringify(formattedLog)], { type: 'application/json' });
        isBeaconSupported = navigator.sendBeacon('/api/logs', blob);
      } catch (beaconError) {
        console.warn('Beacon API usage failed, using fetch:', beaconError);
      }
    }
    
    if (!isBeaconSupported) {
      // fallback to fetch API
      await fetch('/api/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(formattedLog),
        // Do not block page unload, keepalive
        keepalive: true
      });
    }
  } catch (error) {
    // Log reporting failed, do not create new log to avoid infinite loop
    // Store failed log to localStorage for retry later
    try {
      const failedLogs = JSON.parse(localStorage.getItem('failedLogs') || '[]');
      failedLogs.push({
        timestamp: new Date().toISOString(),
        data: logData,
        level
      });
      // Keep only last 20 failed logs (reduce storage usage)
      if (failedLogs.length > 20) {
        failedLogs.splice(0, failedLogs.length - 20);
      }
      localStorage.setItem('failedLogs', JSON.stringify(failedLogs));
    } catch (e) {
      // Silently handle failure, do not create new log
    }
  }
}

/**
 * Retry failed logs
 */
async function retryFailedLogs() {
  try {
    const failedLogs = JSON.parse(localStorage.getItem('failedLogs') || '[]');
    if (failedLogs.length === 0) return;
    
    const logsToRetry = [...failedLogs];
    localStorage.removeItem('failedLogs');
    
    for (const logItem of logsToRetry) {
      await reportLog(logItem.data, logItem.level);
    }
  } catch (error) {
    console.error('Retry failed logs failed:', error);
  }
}

/**
 * Log important user actions to server
 * @param {string} action - Action name
 * @param {Object} details - Action details
 */
export function logUserAction(action, details = {}) {
  // Only log important user actions to reduce noise level
  const importantActions = [
    'login', 'logout', 'payment', 'delete', 'export', 'backup', 'restore', 'error', 'failed'
  ];
  
  const isImportantAction = importantActions.some(important => 
    action.toLowerCase().includes(important)
  );
  
  if (isImportantAction) {
    reportLog({
      type: 'user_action',
      action,
      details
    }, LOG_LEVELS.ERROR);
  }
}

/**
 * Sanitize request body to remove sensitive information
 * @param {any} data - Request data to sanitize
 * @returns {any} Sanitized data
 */
function sanitizeRequestBody(data) {
  if (!data || typeof data !== 'object') {
    return data;
  }
  
  const sensitiveKeys = ['password', 'token', 'auth', 'creditCard', 'cardNumber', 'cvv'];
  const sanitized = { ...data };
  
  // Filter sensitive fields
  sensitiveKeys.forEach(key => {
    if (sanitized[key]) {
      sanitized[key] = '[PROTECTED]';
    }
  });
  
  return sanitized;
}

/**
 * Log API requests - only log problematic requests
 * @param {Object} config - Axios request config
 */
export function logApiRequest(config) {
  // Only log problematic API requests to reduce noise level
  const problematicMethods = ['delete'];  // Only log delete operations
  const problematicPaths = ['payment', 'auth', 'delete'];  // Only log payment, auth, delete paths
  const isProblematic = problematicMethods.includes(config.method) || 
                       problematicPaths.some(path => config.url.includes(path));
  
  if (isProblematic) {
    const sanitizedConfig = {
      method: config.method,
      url: config.url,
      params: config.params,
      timestamp: Date.now()
    };
    
    // Only log request body when it's a problem
    if (config.data && isProblematic) {
      try {
        const dataStr = typeof config.data === 'string' ? config.data : JSON.stringify(config.data);
        if (dataStr.length <= 2 * 1024) { // Limit to 2KB size
          sanitizedConfig.body = sanitizeRequestBody(config.data);
        } else {
          sanitizedConfig.hasBody = true;
          sanitizedConfig.bodyType = typeof config.data;
          sanitizedConfig.bodySize = dataStr.length;
        }
      } catch (e) {
        sanitizedConfig.hasBody = true;
        sanitizedConfig.bodyType = typeof config.data;
      }
    }
    
    // Store request ID to match response
    const requestId = `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    config._requestId = requestId;
    
    reportLog({
      type: 'api_request',
      requestId,
      request: sanitizedConfig
    }, LOG_LEVELS.ERROR);
  }
}

/**
 * Sanitize response body to remove sensitive information
 * @param {any} data - Response data to sanitize
 * @returns {any} Sanitized data
 */
function sanitizeResponseBody(data) {
  if (!data || typeof data !== 'object') {
    return data;
  }
  
  const sensitiveKeys = ['password', 'token', 'auth', 'creditCard', 'cardNumber', 'cvv'];
  
  // Process array
  if (Array.isArray(data)) {
    return data.map(item => sanitizeResponseBody(item));
  }
  
  // Process object
  const sanitized = { ...data };
  
  // Filter sensitive fields
  sensitiveKeys.forEach(key => {
    if (sanitized[key]) {
      sanitized[key] = '[PROTECTED]';
    }
  });
  
  return sanitized;
}

/**
 * Log API responses - only log problematic responses
 * @param {Object} response - Axios response object
 */
export function logApiResponse(response) {
  // Record problematic responses only
  const isErrorResponse = response.status >= 400;
  const isSlowRequest = Date.now() - (response.config.timestamp || Date.now()) > 5000; // Over 5 seconds
  const hasLargeResponse = response.data && JSON.stringify(response.data).length > 50 * 1024; // Over 50KB
  
  if (isErrorResponse || isSlowRequest || hasLargeResponse) {
    const requestId = response.config._requestId;
    
    // Record detailed request information
    const requestInfo = {
      method: response.config.method,
      url: response.config.url,
      params: response.config.params,
      startTime: response.config.timestamp
    };
    
    // Limit response size and filter sensitive information
    let responseData;
    try {
      const dataStr = JSON.stringify(response.data);
      
      if (dataStr.length <= 4 * 1024) { // Limit to 4KB size
        responseData = sanitizeResponseBody(response.data);
      } else {
        responseData = {
          truncated: true,
          size: dataStr.length,
          type: Array.isArray(response.data) ? 'array' : typeof response.data,
          keys: typeof response.data === 'object' ? Object.keys(response.data) : undefined,
          itemCount: Array.isArray(response.data) ? response.data.length : undefined,
          propertyCount: typeof response.data === 'object' ? Object.keys(response.data).length : undefined
        };
      }
    } catch (error) {
      responseData = { 
        error: 'Failed to serialize response data',
        originalType: typeof response.data
      };
    }
    
    const logLevel = isErrorResponse ? LOG_LEVELS.ERROR : LOG_LEVELS.ERROR;
    
    reportLog({
      type: 'api_response',
      requestId,
      request: requestInfo,
      response: {
        status: response.status,
        statusText: response.statusText,
        data: responseData,
        headers: Object.fromEntries(Object.entries(response.headers || {}).filter(
          ([key]) => !['authorization', 'cookie'].includes(key.toLowerCase())
        ))
      },
      duration: Date.now() - (response.config.timestamp || Date.now()),
      timestamp: new Date().toISOString(),
      issues: {
        isErrorResponse,
        isSlowRequest,
        hasLargeResponse
      }
    }, logLevel);
  }
}

/**
 * Log API errors
 * @param {Object} error - Axios error object
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
  }, LOG_LEVELS.ERROR);
}

/**
 * Log page errors
 * @param {Error} error - Error object
 * @param {string} source - Error source
 */
export function logPageError(error, source = 'global') {
  reportLog({
    type: 'page_error',
    error: {
      message: error.message,
      stack: error.stack,
      source
    }
  }, LOG_LEVELS.ERROR);
}

/**
 * Log performance metrics
 * @param {string} metricName - Metric name
 * @param {number} value - Metric value
 * @param {Object} context - Context information
 */
export function logPerformanceMetric(metricName, value, context = {}) {
  // Record performance metrics only
  const performanceIssues = {
    'page_load_time': 3000, // Over 3 seconds
    'api_response_time': 10000, // Over 10 seconds
    'memory_usage': 100 * 1024 * 1024, // Over 100MB
    'dom_elements': 5000 // Over 5000 DOM elements
  };
  
  const threshold = performanceIssues[metricName];
  if (threshold && value > threshold) {
    reportLog({
      type: 'performance',
      metric: metricName,
      value,
      context,
      threshold
    }, LOG_LEVELS.ERROR);
  }
}

/**
 * Initialize global error monitoring
 */
export function initGlobalErrorMonitoring() {
  // Listen for uncaught JavaScript errors
  window.addEventListener('error', (event) => {
    logPageError(new Error(event.message), `line ${event.lineno}, col ${event.colno}, ${event.filename}`);
  });
  
  // Listen for unhandled Promise rejections
  window.addEventListener('unhandledrejection', (event) => {
    logPageError(
      event.reason || new Error('Promise rejection'), 
      'unhandledrejection'
    );
  });
}

/**
 * Try to report failed logs again
 */
export function tryReportFailedLogs() {
  retryFailedLogs();
}

/**
 * Initialize console logging - simplified version
 * @param {Object} options - Configuration options
 * @param {Array<string>} options.levels - Log levels to capture, default ['error', 'warn']
 * @param {number} options.maxLength - Maximum length of log messages, default 2000
 */
export function initConsoleLogging(options = {}) {
  const {
    levels = ['error', 'warn'], // Capture error and warn levels by default
    maxLength = 2000 // Reduce maximum length
  } = options;

  // Store original console methods
  const originalConsole = {};

  levels.forEach(level => {
    if (typeof console[level] === 'function') {
      originalConsole[level] = console[level];
      
      // Override console methods
      console[level] = function(...args) {
        // Call original method to ensure console output is visible
        originalConsole[level].apply(console, args);
        
        // Process log parameters
        try {
          // Serialize parameters to handle different types of values
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
              return '[unserializable value]';
            }
          });
          
          // Limit log message length
          let logMessage = formattedArgs.join(' ');
          if (logMessage.length > maxLength) {
            logMessage = logMessage.substring(0, maxLength) + '... [truncated]';
          }
          
          // Report console logs - only report errors to avoid overlogging
          if (level === 'error') {
            reportLog({
              type: 'console_log',
              level,
              message: logMessage,
              timestamp: new Date().toISOString()
            }, LOG_LEVELS.ERROR);
          }
        } catch (e) {
          // If logging fails, use original console to record error but do not report
          originalConsole.error('Console log capture failed:', e);
        }
      };
    }
  });
}

/**
 * Export default object
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
  initConsoleLogging,
  setLogLevel
};