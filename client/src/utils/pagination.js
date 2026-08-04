/**
 * Pagination data fetch utility
 * Provides efficient pagination loading mechanism, avoiding large data retrieval
 */

// 分页配置
const DEFAULT_PAGINATION_CONFIG = {
  pageSize: 20,           // Default page size
  maxConcurrent: 3,        // Maximum concurrent requests
  retryTimes: 3,           // Retry times
  retryDelay: 1000,        // Retry delay (ms)
  timeout: 30000,          // Request timeout (ms)
  enableProgress: true,    // Whether to show progress indicator
};

/**
 * Pagination data fetch main function
 * @param {Object} options - Configuration options
 * @param {Function} options.apiCall - API call function, accepting {page, limit} parameters
 * @param {number} options.pageSize - Page size
 * @param {number} options.maxConcurrent - Maximum concurrent requests
 * @param {Function} options.onProgress - Progress callback function
 * @param {Function} options.onError - Error callback function
 * @param {AbortSignal} options.signal - Abort signal
 * @returns {Promise<Array>} - Merged data from all pages
 */
export async function fetchAllPages(options = {}) {
  const config = { ...DEFAULT_PAGINATION_CONFIG, ...options };
  const {
    apiCall,
    pageSize = config.pageSize,
    maxConcurrent = config.maxConcurrent,
    onProgress,
    onError,
    signal
  } = config;

  if (!apiCall || typeof apiCall !== 'function') {
    throw new Error('apiCall parameter must be a function');
  }

  const allData = [];
  let totalCount = 0;
  let isCompleted = false;

  // Check cancellation signal
  const checkCancellation = () => {
    if (signal?.aborted) {
      throw new Error('Operation canceled');
    }
  };

  try {
    // First get total count first
    checkCancellation();
    const firstResponse = await makeApiCallWithRetry(
      () => apiCall({ page: 1, limit: 1 }),
      config,
      signal
    );

    totalCount = getTotalCount(firstResponse);
    
    if (totalCount === 0) {
      console.log('fetchAllPages: No data to load');
      return [];
    }

    if (totalCount <= pageSize) {
      // single page data, fetch all
      const singlePageData = getDataFromResponse(firstResponse);
      console.log(`fetchAllPages: Data count is small (${totalCount} records), single page fetch completed`);
      return singlePageData;
    }

    console.log(`fetchAllPages: Start loading, ${totalCount} records, ${pageSize} records per page`);

    // Calculate total pages
    const totalPages = Math.ceil(totalCount / pageSize);
    
    // Batch load data
    const batches = [];
    for (let page = 1; page <= totalPages; page++) {
      batches.push(page);
    }

    // Concurrent control logic
    const batchSize = Math.min(maxConcurrent, batches.length);
    const results = [];

    for (let i = 0; i < batches.length; i += batchSize) {
      checkCancellation();
      
      const batch = batches.slice(i, i + batchSize);
      const batchPromises = batch.map(page => 
        makeApiCallWithRetry(
          () => apiCall({ page, limit: pageSize }),
          config,
          signal
        ).then(response => ({ page, data: getDataFromResponse(response) }))
      );

      try {
        const batchResults = await Promise.all(batchPromises);
        results.push(...batchResults);
        
        // Sort by page number
        results.sort((a, b) => a.page - b.page);
        
        // Merge data
        allData.length = 0;
        results.forEach(result => {
          allData.push(...result.data);
        });

        // Progress callback
        if (onProgress && typeof onProgress === 'function') {
          const loadedCount = allData.length;
          const progress = Math.round((loadedCount / totalCount) * 100);
          onProgress({
            loaded: loadedCount,
            total: totalCount,
            progress,
            currentPage: Math.max(...results.map(r => r.page))
          });
        }

        console.log(`fetchAllPages: Loaded ${allData.length}/${totalCount} records (${Math.round((allData.length / totalCount) * 100)}%)`);
        
      } catch (batchError) {
        console.error('Batch load failed:', batchError);
        if (onError && typeof onError === 'function') {
          onError(batchError);
        }
        throw batchError;
      }
    }

    isCompleted = true;
    console.log(`fetchAllPages: Completed! Total ${allData.length} records`);

    return allData;

  } catch (error) {
    if (error.message === 'Operation canceled') {
      console.log('fetchAllPages: Operation canceled by user');
      throw error;
    }
    
    console.error('fetchAllPages: Load failed:', error);
    throw error;
  }
}

/**
 * �
 * @param {Function} apiCall - API call function
 * @param {Object} config - Configuration
 * @param {AbortSignal} signal - Abort signal
 * @returns {Promise} - API response
 */
async function makeApiCallWithRetry(apiCall, config, signal) {
  let lastError;
  
  for (let attempt = 0; attempt <= config.retryTimes; attempt++) {
    try {
      checkSignal(signal);
      
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), config.timeout);
      
      const response = await apiCall();
      clearTimeout(timeoutId);
      
      return response;
    } catch (error) {
      lastError = error;
      console.warn(`API call failed (attempt ${attempt + 1}/${config.retryTimes + 1}):`, error.message);
      
      if (attempt < config.retryTimes) {
        await delay(config.retryDelay * Math.pow(2, attempt)); // Exponential backoff
      }
    }
  }
  
  throw lastError;
}

/**
 * Check cancellation signal
 * @param {AbortSignal} signal Abort signal
 */
function checkSignal(signal) {
  if (signal?.aborted) {
    throw new Error('Operation canceled');
  }
}

/**
 * Delay function
 * @param {number} ms - Delay milliseconds
 * @returns {Promise}
 */
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Get total count from response data
 * @param {Object} response - API response
 * @returns {number} - Total count of records
 */
function getTotalCount(response) {
  // Try multiple possible response formats
  if (response?.data?.total !== undefined) {
    return Number(response.data.total);
  }
  if (response?.total !== undefined) {
    return Number(response.total);
  }
  if (response?.data?.length !== undefined) {
    return Number(response.data.length);
  }
  if (Array.isArray(response?.data)) {
    return Number(response.data.length);
  }
  if (Array.isArray(response)) {
    return Number(response.length);
  }
  
  console.warn('No total count found in response, default to 1');
  return 1;
}

/**
 * Get data array from response data
 * @param {Object} response - API response
 * @returns {Array} - Data array
 */
function getDataFromResponse(response) {
  // Try multiple possible response formats
  if (response?.data?.data && Array.isArray(response.data.data)) {
    return response.data.data;
  }
  if (response?.data && Array.isArray(response.data)) {
    return response.data;
  }
  if (Array.isArray(response)) {
    return response;
  }
  
  console.warn('Cannot get data array from response, return empty array');
  return [];
}

/**
 * Cancel pagination fetch operation
 * @param {AbortController} controller - Cancellation controller
 */
export function cancelPagination(controller) {
  if (controller) {
    controller.abort();
    console.log('Pagination fetch operation canceled by user');
  }
}

/**
 * Create cancellation controller for pagination fetch operation
 * @returns {AbortController} - Cancellation controller
 */
export function createCancellationController() {
  return new AbortController();
}

/**
 * Preload data (background silent loading)
 * @param {Object} options - Configuration options
 * @returns {Promise<Array>} - Preloaded data
 */
export async function preloadData(options = {}) {
  const config = { 
    ...DEFAULT_PAGINATION_CONFIG, 
    ...options,
    enableProgress: false // Preload without progress display
  };
  
  return fetchAllPages(config);
}

/**
 * Get pagination statistics
 * @param {number} totalCount - Total data count
 * @param {number} pageSize - Page size
 * @returns {Object} - Pagination statistics
 */
export function getPaginationInfo(totalCount, pageSize) {
  const totalPages = Math.ceil(totalCount / pageSize);
  return {
    totalCount,
    pageSize,
    totalPages,
    estimatedTime: Math.ceil(totalCount / 1000 * 2), // Estimated time in seconds
    recommendedBatchSize: Math.min(Math.ceil(1000 / pageSize), 5)
  };
}

export default {
  fetchAllPages,
  cancelPagination,
  createCancellationController,
  preloadData,
  getPaginationInfo,
  DEFAULT_PAGINATION_CONFIG
};