import dayjs from 'dayjs';

// 根据语言格式化日期
export const formatDateByLocale = (date, locale) => {
  const d = dayjs(date);
  if (!d.isValid()) return '';
  
  // 使用dayjs的本地化功能，不需要手动定义月份名称
  try {
    // 先尝试使用当前设置的语言环境
    const currentLocale = dayjs.locale();
    
    // 根据不同语言环境返回不同格式
    switch (locale || currentLocale) {
    case 'en-US':
      return d.format('MMMM D, YYYY'); // January 1, 2023
    case 'zh-CN':
    case 'zh-TW':
      return d.format('YYYY年MM月DD日'); // 2023年01月01日
    default:
      return d.format('YYYY-MM-DD'); // 默认格式
    }
  } catch (error) {
    console.error('日期格式化错误:', error);
    return d.format('YYYY-MM-DD');
  }
};

// 根据语言格式化月份标签
export const formatMonthLabelByLocale = (yearMonth, locale) => {
  const d = dayjs(yearMonth);
  if (!d.isValid()) return '';
  
  // 使用dayjs的本地化功能来格式化月份
  try {
    // 获取当前dayjs设置的语言环境
    const currentLocale = dayjs.locale();
    // 使用传入的locale参数或当前设置的语言
    const targetLocale = locale || currentLocale;
    
    // 根据不同语言环境返回不同的月份格式
    switch (targetLocale) {
    case 'zh-CN':
    case 'zh-tw': // dayjs使用小写的zh-tw
    case 'zh-TW': // 兼容大写的zh-TW
      return d.format('YYYY年MM月'); // 2023年01月
    case 'en-US':
      return d.format('MMMM YYYY'); // January 2023
    default:
      // 对于其他语言，尝试使用其本地化格式
      // 如果失败则回退到YYYY-MM格式
      try {
        // 保存当前语言设置
        const originalLocale = dayjs.locale();
        // 临时切换到目标语言
        dayjs.locale(targetLocale);
        // 尝试获取本地化的月份名称格式
        const formatted = d.format('MMMM YYYY');
        // 恢复原来的语言设置
        dayjs.locale(originalLocale);
        return formatted;
      } catch (e) {
        return d.format('YYYY-MM');
      }
    }
  } catch (error) {
    console.error('月份格式化错误:', error);
    return d.format('YYYY-MM');
  }
};
