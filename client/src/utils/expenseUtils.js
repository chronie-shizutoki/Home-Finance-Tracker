// expenseUtils.js
// 类型颜色映射缓存
const typeColorCache = {};

/**
 * 获取消费类型的颜色
 * @param {string} type 消费类型
 * @returns {string} HSL颜色字符串
 */
export function getTypeColor (type, isDarkMode = false) {
  // 为不同模式使用不同的缓存键
  const cacheKey = isDarkMode ? `${type}_dark` : type;
  
  if (!typeColorCache[cacheKey]) {
    // 生成随机但一致的颜色
    const hue = Math.floor(Math.random() * 360);
    // 深色模式下降低亮度，提高饱和度
    const saturation = isDarkMode ? 80 : 70;
    const lightness = isDarkMode ? 65 : 85;
    typeColorCache[cacheKey] = `hsl(${hue}, ${saturation}%, ${lightness}%)`;
  }
  return typeColorCache[cacheKey];
}

/**
 * 计算分页可见页面范围
 * @param {number} currentPage 当前页码
 * @param {number} totalPages 总页数
 * @returns {Array} 可见页码数组
 */
export function calculateVisiblePages (currentPage, totalPages) {
  const pages = [];

  if (totalPages <= 5) {
    for (let i = 1; i <= totalPages; i++) {
      pages.push(i);
    }
  } else {
    const start = Math.max(1, currentPage - 2);
    const end = Math.min(totalPages, currentPage + 2);

    if (start > 1) pages.push(1);
    if (start > 2) pages.push('...');

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    if (end < totalPages - 1) pages.push('...');
    if (end < totalPages) pages.push(totalPages);
  }

  return pages;
}
