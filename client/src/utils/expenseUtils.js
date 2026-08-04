// expenseUtils.js
// type color cache
const typeColorCache = {};

/**
 * Get color for expense type
 * @param {string} type Expense type
 * @returns {string} HSL color string
 */
export function getTypeColor (type, isDarkMode = false) {
  // Use different cache key for dark mode
  const cacheKey = isDarkMode ? `${type}_dark` : type;
  
  if (!typeColorCache[cacheKey]) {
    // Generate random but consistent color for the type
    const hue = Math.floor(Math.random() * 360);
    // Dark mode reduces lightness and increases saturation
    const saturation = isDarkMode ? 80 : 70;
    const lightness = isDarkMode ? 65 : 85;
    typeColorCache[cacheKey] = `hsl(${hue}, ${saturation}%, ${lightness}%)`;
  }
  return typeColorCache[cacheKey];
}

/**
 * Calculate visible pages range for pagination pagination
 * @param {number} currentPage Current page number
 * @param {number} totalPages Total pages
 * @returns {Array} Visible pages array
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
