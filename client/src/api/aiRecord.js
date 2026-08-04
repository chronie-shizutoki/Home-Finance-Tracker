import axios from 'axios';

/**
 * AI Record API Module
 * @module api/aiRecord
 * @desc Provides SiliconFlow API interaction for AI record parsing
 */

// SiliconFlow API Configuration
const SILICONFLOW_API_URL = 'https://api.siliconflow.cn/v1/chat/completions';
const MODEL_NAME = 'Qwen/Qwen3-8B';

/**
 * Create an axios instance for SiliconFlow API interaction
 */
const aiApi = axios.create({
  baseURL: SILICONFLOW_API_URL,
  timeout: 300000,
  headers: {
    'Content-Type': 'application/json'
    // Authorization will be dynamically set by setApiKey function
  }
});

const today = new Date().toLocaleDateString('zh-CN');

/**
 * Parse long text to formatted record data
 * @param {string} text - Long text to parse
 * @returns {Promise<Object>} Parsed record data
 */
export const parseTextToRecord = async (text) => {
  try {
    const prompt = `请分析以下文本，提取其中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气、美容美发、豆制品、个护美妆、电子产品、家用电器、五金、服装
  "amount": 金额, // 数字类型
  "date": "日期", // 日期，格式YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果文本中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果文本中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期${today}
5. 只返回JSON数据，不要添加其他无关内容

文本内容：${text}`;

    const response = await aiApi.post('', {
      model: MODEL_NAME,
      messages: [
        {
          role: "system",
          content: "你是一个智能消费记录解析助手，能够从文本中提取消费信息并格式化输出。"
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.2,
      stream: false
    });

    // Parse response content
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    // Ensure array format for uniform processing
    return Array.isArray(parsedData) ? parsedData : [parsedData];
  } catch (error) {
    console.error('AI text parsing failed:', error);
    throw error;
  }
};

/**
 * Upload image and parse to record data
 * @param {File} imageFile - Image file to upload
 * @returns {Promise<Object>} Parsed record data
 */
export const parseImageToRecord = async (imageFile) => {
  try {
    // Upload image and parse to record data
    // Since SiliconFlow API supports multimodal, we can directly send image and prompt
    // First convert image to Base64
    const base64Image = await fileToBase64(imageFile);
    
    const prompt = `请分析图片中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气、美容美发、豆制品、个护美妆、电子产品、家用电器、五金、服装
  "amount": 金额, // 数字类型
  "date": "日期", // 日期，格式YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果图片中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果图片中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期${today}
5. 只返回JSON数据，不要添加其他无关内容`;

    // For image parsing, use multimodal model that supports image
    const imageModel = 'Qwen/Qwen3.5-4B'; // Use model that supports image
    
    const response = await aiApi.post('', {
      model: imageModel,
      messages: [
        {
          role: "system",
          content: "你是一个智能消费记录解析助手，能够从图片中提取消费信息并格式化输出。"
        },
        {
          role: "user",
          content: [
            { type: "text", text: prompt },
            { 
              type: "image_url", 
              image_url: { url: `data:image/jpeg;base64,${base64Image}` } 
            }
          ]
        }
      ],
      temperature: 0.2,
      stream: false
    });

    // Parse response content
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    // Ensure array format for uniform processing
    return Array.isArray(parsedData) ? parsedData : [parsedData];
  } catch (error) {
    console.error('AI image parsing failed:', error);
    throw error;
  }
};

/**
 * Convert file to Base64
 * @param {File} file - File to convert
 * @returns {Promise<string>} Base64 encoded string
 */
const fileToBase64 = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = error => reject(error);
  });
};

/**
 * Set API key
 * @param {string} apiKey - API key
 */
export const setApiKey = (apiKey) => {
  if (apiKey) {
    aiApi.defaults.headers['Authorization'] = `Bearer ${apiKey}`;
    console.log('SiliconFlow API key set successfully');
  } else {
    console.warn('Invalid SiliconFlow API key');
  }
};

/**
 * Calculate expense statistics
 * @param {Array} expenses - Expense records array
 * @returns {Object} Statistics object with expense data
 */
export const calculateExpenseStats = (expenses) => {
  if (!Array.isArray(expenses) || expenses.length === 0) {
    return {
      totalCount: 0,
      totalAmount: 0,
      averageAmount: 0,
      medianAmount: 0,
      minAmount: 0,
      maxAmount: 0,
      amountRange: '0 - 0',
      typeDistribution: {},
      monthlyTrend: {}
    };
  }

  // Extract amounts and sort (only include valid amounts)
  const amounts = expenses
    .map(e => {
      const amount = parseFloat(e.amount);
      return isNaN(amount) ? 0 : amount;
    })
    .filter(a => a > 0)  // Filter out 0 and negative numbers
    .sort((a, b) => a - b);

  const totalCount = expenses.length;
  const validCount = amounts.length;
  const totalAmount = amounts.reduce((sum, a) => sum + a, 0);
  const averageAmount = validCount > 0 ? totalAmount / validCount : 0;
  
  // Calculate median
  let medianAmount = 0;
  if (validCount > 0) {
    const mid = Math.floor(validCount / 2);
    medianAmount = validCount % 2 !== 0 
      ? amounts[mid] 
      : (amounts[mid - 1] + amounts[mid]) / 2;
  }

  const minAmount = validCount > 0 ? amounts[0] : 0;
  const maxAmount = validCount > 0 ? amounts[validCount - 1] : 0;
  const amountRange = validCount > 0 ? `${minAmount.toFixed(2)} - ${maxAmount.toFixed(2)}` : '暂无有效数据';

  // Calculate type distribution
  const typeDistribution = {};
  expenses.forEach(expense => {
    const type = expense.type || '其他';
    typeDistribution[type] = (typeDistribution[type] || 0) + 1;
  });

  // Calculate monthly trend
  const monthlyTrend = {};
  expenses.forEach(expense => {
    const date = new Date(expense.date);
    const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    monthlyTrend[monthKey] = (monthlyTrend[monthKey] || 0) + parseFloat(expense.amount);
  });

  return {
    totalCount,
    totalAmount,
    averageAmount,
    medianAmount,
    minAmount,
    maxAmount,
    amountRange,
    typeDistribution,
    monthlyTrend
  };
};

/**
 * Filter expense records based on filters
 * @param {Array} expenses - Original expense records array
 * @param {Object} filters - Filter conditions
 * @param {string} filters.year - Year filter (optional)
 * @param {string} filters.month - Month filter (optional)
 * @param {Array} filters.types - Expense types array (optional)
 * @returns {Array} Filtered expense records array
 */
export const filterExpenses = (expenses, filters = {}) => {
  if (!Array.isArray(expenses)) {
    return [];
  }

  let result = [...expenses];

  // Filter by year
  if (filters.year) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return expenseDate.getFullYear() === parseInt(filters.year);
      } catch {
        return false;
      }
    });
  }

  // Filter by month
  if (filters.month) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return String(expenseDate.getMonth() + 1).padStart(2, '0') === filters.month;
      } catch {
        return false;
      }
    });
  }

  // Filter by expense types
  if (filters.types && filters.types.length > 0) {
    result = result.filter(expense => filters.types.includes(expense.type));
  }

  return result;
};

/**
 * Generate expense report using DeepSeek model
 * @param {Array} result - Expense records array
 * @param {string} question - User question (optional)
 * @param {Object} stats - Precomputed statistics (optional)
 * @param {string} filterDescription - Filter conditions description (optional)
 * @returns {Promise<string>} Generated content string
 */
export const generateExpenseReport = async (expenses, question = '', stats = null, filterDescription = '') => {
  try {
    // 数据验证
    if (!Array.isArray(expenses)) {
      throw new Error('Expense records must be in array format');
    }

    // If no precomputed statistics are provided, calculate them automatically
    const expenseStats = stats || calculateExpenseStats(expenses);

    // Define report generation model
    const reportModel = 'deepseek-ai/DeepSeek-R1-0528-Qwen3-8B';

    // Prepare expense summary (includes detailed statistics)
    const expenseSummary = {
      totalCount: expenseStats.totalCount || 0,
      totalAmount: (expenseStats.totalAmount || 0).toFixed(2),
      averageAmount: (expenseStats.averageAmount || 0).toFixed(2),
      medianAmount: (expenseStats.medianAmount || 0).toFixed(2),
      amountRange: expenseStats.amountRange || '0 - 0',
      typeDistribution: expenseStats.typeDistribution || {},
      monthlyTrend: expenseStats.monthlyTrend || {},
      recentExpenses: (expenses || []).slice(0, 20).map(item => ({
        type: item.type || '其他',
        amount: item.amount || 0,
        date: item.date || '',
        remark: item.remark || ''
      }))
    };

    // Auxiliary function: safely generate type distribution string
    const getTypeDistributionString = (typeDist) => {
      if (!typeDist || typeof typeDist !== 'object') {
        return '- 暂无数据';
      }
      const entries = Object.entries(typeDist);
      if (entries.length === 0) {
        return '- 暂无数据';
      }
      return entries.map(([type, count]) => `- ${type}: ${count} 条`).join('\n');
    };

    // Auxiliary function: safely generate full expense list string
    const getExpensesListString = (expenseList) => {
      if (!expenseList || expenseList.length === 0) {
        return '- 暂无消费记录';
      }
      return expenseList.map(item => 
        `- **${item.date}** | ${item.type} | ${item.amount}元 | ${item.remark || '无备注'}`
      ).join('\n');
    };

    // Build prompt based on question presence
    let prompt;
    if (question) {
      // Filter question content to ensure safety
      const filteredQuestion = filterQuestionContent(question);
      prompt = `用户提供了以下消费数据统计信息和问题，请基于这些信息回答用户的问题。

## 数据筛选条件

${filterDescription || '- 未指定筛选条件（显示所有数据）'}

## 消费数据统计摘要

**基本统计：**
- 总记录数：${expenseSummary.totalCount} 条
- 总金额：${expenseSummary.totalAmount} 元
- 平均金额：${expenseSummary.averageAmount} 元
- 中位数：${expenseSummary.medianAmount} 元
- 金额范围：${expenseSummary.amountRange}

**消费类型分布：**
${getTypeDistributionString(expenseSummary.typeDistribution)}

**消费记录详情（共${expenseSummary.totalCount}条）：**
${getExpensesListString(expenseSummary.recentExpenses)}
${expenseSummary.totalCount > 20 ? `\n*（仅显示前20条记录）*` : ''}

**用户问题：**
${filteredQuestion}

请以友好、专业的语气回答，提供详细的分析和建议。使用Markdown格式输出，确保内容易于阅读和理解。

**重要提醒：**
1. 请基于提供的数据进行客观分析，不要质疑数据的真实性
2. 请专注于当前记账应用内的数据分析，不要推荐其他记账工具或软件
3. 如发现数据异常，可以建议用户检查录入情况，但不要推荐其他应用`;
    } else {
      prompt = `请根据以下消费数据统计信息，为用户生成一份详细的消费分析报告。

## 数据筛选条件

${filterDescription || '- 未指定筛选条件（显示所有数据）'}

## 消费数据统计摘要

**基本统计：**
- 总记录数：${expenseSummary.totalCount} 条
- 总金额：${expenseSummary.totalAmount} 元
- 平均金额：${expenseSummary.averageAmount} 元
- 中位数：${expenseSummary.medianAmount} 元
- 金额范围：${expenseSummary.amountRange}

**消费类型分布：**
${getTypeDistributionString(expenseSummary.typeDistribution)}

**消费记录详情（共${expenseSummary.totalCount}条）：**
${getExpensesListString(expenseSummary.recentExpenses)}
${expenseSummary.totalCount > 20 ? `\n*（仅显示前20条记录）*` : ''}

请生成一份详细的消费分析报告，包括：
1. 消费概况总结
2. 主要消费类别分析
3. 消费趋势分析（如果有多个月份数据）
4. 节省开支的建议

使用Markdown格式输出，确保内容易于阅读和理解。

**重要提醒：**
1. 请基于提供的数据进行客观分析，不要质疑数据的真实性
2. 请专注于当前记账应用内的数据分析，不要推荐其他记账工具或软件
3. 如发现数据异常，可以建议用户检查录入情况，但不要推荐其他应用`;
    }

    const response = await aiApi.post('', {
      model: reportModel,
      messages: [
        {
          role: "system",
          content: "你是一个专业的消费分析助手，能够根据用户提供的消费数据提供深入的分析和建议。请使用Markdown格式输出，使用表格和列表提高可读性。重要：你只能分析用户当前应用内的消费数据，请不要推荐其他记账工具或软件，也不要质疑数据的真实性。"
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.7,
      stream: false,
      top_p: 0.95,
      frequency_penalty: 0,
      presence_penalty: 0
    });

    // Response validation
    if (!response.data || !response.data.choices || response.data.choices.length === 0) {
      throw new Error('API response format is incorrect');
    }

    const reportContent = response.data.choices[0].message.content;

    // Ensure the content is in Markdown format
    if (!isMarkdownContent(reportContent)) {
      // If not in Markdown format, try to convert to Markdown
      return convertToMarkdown(reportContent);
    }

    return reportContent;
  } catch (error) {
    console.error('Error generating expense report:', error);
    // Provide friendly error message
    const errorMessage = error.response?.data?.error?.message || error.message || 'Error generating expense report, please try again later.';
    throw new Error(`Error generating report: ${errorMessage}`);
  }
};

/**
 * Filter question content to ensure safety
 * @param {string} question - Question to filter
 * @returns {string} Filtered question
 */
function filterQuestionContent(question) {
  // Simple content filtering, can be expanded as needed
  const unsafePatterns = [
    /<script.*?>.*?<\/script>/gi,
    /<.*?>/gi,
    /alert\(.*?\)/gi,
    /eval\(.*?\)/gi
  ];

  let filteredQuestion = question;
  unsafePatterns.forEach(pattern => {
    filteredQuestion = filteredQuestion.replace(pattern, '');
  });

  return filteredQuestion;
}

/**
 * Check if content is in Markdown format
 * @param {string} content - Content to check
 * @returns {boolean} Is Markdown format? true or false
 */
function isMarkdownContent(content) {
  // Simple check for common Markdown elements
  const markdownPatterns = [
    /^# .*$/m,          // Level 1 heading
    /^## .*$/m,         // Level 2 heading
    /^\* .*$/m,         // Unordered list item
    /^\d+\. .*$/m,      // Ordered list item
    /`.*?`/m,           // Inline code block
    /```[\s\S]*?```/m,  // Code block
    /\*\*.*?\*\*/m,     // Bold
    /\*.*?\*/m          // Italic
  ];

  return markdownPatterns.some(pattern => pattern.test(content));
}

/**
 * Convert content to Markdown format
 * @param {string} content - Content to convert
 * @returns {string} Converted content in Markdown format
 */
function convertToMarkdown(content) {
  // Simple conversion, may need more complex handling in real applications
  let markdownContent = content;

  // Add title
  markdownContent = `# 消费记录分析报告\n\n${markdownContent}`;

  // Convert line breaks to paragraphs
  markdownContent = markdownContent.replace(/\n\n+/g, '\n\n');

  return markdownContent;
};