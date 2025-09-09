import axios from 'axios';

/**
 * AI智能记录API模块
 * @module api/aiRecord
 * @desc 提供与SiliconFlow API交互，实现AI智能解析记录功能
 */

// SiliconFlow API配置
const SILICONFLOW_API_URL = 'https://api.siliconflow.cn/v1/chat/completions';
const MODEL_NAME = 'deepseek-ai/DeepSeek-R1-0528-Qwen3-8B';

/**
 * 创建与SiliconFlow API交互的axios实例
 */
const aiApi = axios.create({
  baseURL: SILICONFLOW_API_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
    // 注意：Authorization头部将通过setApiKey函数动态设置
  }
});

/**
 * 解析长文本为格式化的记录数据
 * @param {string} text - 要解析的长文本
 * @returns {Promise<Object>} 解析后的格式化记录数据
 */
export const parseTextToRecord = async (text) => {
  try {
    const prompt = `请分析以下文本，提取其中的消费信息，并以JSON格式输出：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气
  "amount": 金额, // 数字类型
  "time": "日期", // YYYY-MM-DD格式
  "remark": "备注" // 可选的详细说明
}

请注意：
1. 如果文本中没有明确的消费类型，请根据内容选择最合适的
2. 如果没有明确的日期，请使用当前日期
3. 只返回JSON对象，不要添加其他无关内容

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

    // 解析响应内容
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    return parsedData;
  } catch (error) {
    console.error('AI文本解析失败:', error);
    throw error;
  }
};

/**
 * 上传图片并解析为记录数据
 * @param {File} imageFile - 要上传的图片文件
 * @returns {Promise<Object>} 解析后的格式化记录数据
 */
export const parseImageToRecord = async (imageFile) => {
  try {
    // 由于SiliconFlow API支持多模态，我们可以直接发送图片和提示
    // 首先将图片转换为Base64
    const base64Image = await fileToBase64(imageFile);
    
    const prompt = `请分析图片中的消费信息，并以JSON格式输出：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气
  "amount": 金额, // 数字类型
  "time": "日期", // YYYY-MM-DD格式
  "remark": "备注" // 可选的详细说明
}

请注意：
1. 如果图片中没有明确的消费类型，请根据内容选择最合适的
2. 如果没有明确的日期，请使用当前日期
3. 只返回JSON对象，不要添加其他无关内容`;

    // 对于图片解析，使用支持多模态的模型
    const imageModel = 'THUDM/GLM-4.1V-9B-Thinking'; // 使用支持图片的模型
    
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

    // 解析响应内容
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    return parsedData;
  } catch (error) {
    console.error('AI图片解析失败:', error);
    throw error;
  }
};

/**
 * 将文件转换为Base64
 * @param {File} file - 要转换的文件
 * @returns {Promise<string>} Base64编码的字符串
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
 * 设置API密钥
 * @param {string} apiKey - 用户的API密钥
 */
export const setApiKey = (apiKey) => {
  if (apiKey) {
    aiApi.defaults.headers['Authorization'] = `Bearer ${apiKey}`;
    console.log('SiliconFlow API密钥已设置');
  } else {
    console.warn('未提供有效的SiliconFlow API密钥');
  }
};