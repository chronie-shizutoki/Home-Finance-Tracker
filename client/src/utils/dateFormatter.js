const isValidDate = (date) => {
  return date instanceof Date && !isNaN(date.getTime());
};

const padZero = (num) => {
  return num.toString().padStart(2, '0');
};

const parseDate = (date) => {
  if (date instanceof Date) return date;
  return new Date(date);
};

export const formatDateByLocale = (date, locale) => {
  const d = parseDate(date);
  if (!isValidDate(d)) return '';

  try {
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    }).format(d);
  } catch (error) {
    console.error('formatDateByLocale error:', error);
    return `${d.getFullYear()}-${padZero(d.getMonth() + 1)}-${padZero(d.getDate())}`;
  }
};
export const formatMonthLabelByLocale = (yearMonth, locale) => {
  const d = parseDate(yearMonth);
  if (!isValidDate(d)) return '';

  try {
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: 'long'
    }).format(d);
  } catch (error) {
    console.error('formatMonthLabelByLocale error:', error);
    return `${d.getFullYear()}-${d.getMonth() + 1}`;
  }
};
