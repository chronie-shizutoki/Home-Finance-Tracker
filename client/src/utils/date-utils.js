/**
 * Format date as relative date (today, yesterday, X days ago, etc.)
 * @param {string} dateString - Date string (YYYY-MM-DD)
 * @param {Function} t - Vue i18n translation function
 * @returns {string} Formatted relative date string
 */
export const formatRelativeDate = (dateString, t) => {
  try {
    console.log('formatRelativeDate called with:', dateString);
    const [year, month, day] = dateString.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    
    today.setHours(0, 0, 0, 0);
    
    const diffTime = today - date;
    const daysBetween = Math.floor(diffTime / (1000 * 60 * 60 * 24));
    
    console.log('Date calculation:', { date, today, diffTime, daysBetween });
    
    const dayOfWeek = date.getDay();
    const weekdayKeys = [
      'common.sunday',
      'common.monday',
      'common.tuesday',
      'common.wednesday',
      'common.thursday',
      'common.friday',
      'common.saturday'
    ];
    const weekdayString = t(weekdayKeys[dayOfWeek]);
    
    let result;
    if (daysBetween === 0) {
      result = `${t('common.date_today')}（${weekdayString}）`;
    } else if (daysBetween === 1) {
      result = `${t('common.date_yesterday')}（${weekdayString}）`;
    } else if (daysBetween >= 2 && daysBetween <= 6) {
      result = `${t('common.date_days_ago', { days: daysBetween })}（${weekdayString}）`;
    } else {
      result = `${dateString}（${weekdayString}）`;
    }
    
    console.log('formatRelativeDate result:', result);
    return result;
  } catch (e) {
    console.error('formatRelativeDate error:', e);
    return dateString;
  }
};

/**
 * Format date as YYYY-MM-DD format
 * @param {string|Date} date - Date string or Date object
 * @returns {string} Formatted date string
 */
export const formatDate = (date) => {
  if (!date) return '';
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(targetDate.getTime())) return '';

  const year = targetDate.getFullYear();
  const month = String(targetDate.getMonth() + 1).padStart(2, '0');
  const day = String(targetDate.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
};

/**
 * Check if date has expired
 * @param {string|Date} date - Date string or Date object
 * @returns {boolean} Whether expired
 */
export const isExpired = (date) => {
  if (!date) return false;
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return targetDate < today;
};

/**
 * Check if date is soon expired (within 7 days)
 * @param {string|Date} date - Date string or Date object
 * @returns {boolean} Whether soon expired
 */
export const isSoonExpired = (date) => {
  if (!date) return false;
  const targetDate = typeof date === 'string' ? new Date(date) : date;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const oneWeekLater = new Date(today);
  oneWeekLater.setDate(today.getDate() + 7);
  return targetDate >= today && targetDate <= oneWeekLater;
};

/**
 * Get current date as string (YYYY-MM-DD)
 * @returns {string} Current date
 */
export const getCurrentDate = () => {
  return formatDate(new Date());
};

/**
 * Calculate days difference between two dates
 * @param {string|Date} startDate - Start date
 * @param {string|Date} endDate - End date
 * @returns {number} Days difference
 */
export const getDaysDifference = (startDate, endDate) => {
  if (!startDate || !endDate) return 0;
  const start = typeof startDate === 'string' ? new Date(startDate) : startDate;
  const end = typeof endDate === 'string' ? new Date(endDate) : endDate;
  const diffTime = end - start;
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
};
