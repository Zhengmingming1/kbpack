import dayjs from 'dayjs';

export function formatDate(value?: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

export function formatRelativeDate(value?: string | null) {
  if (!value) return '—';
  const date = dayjs(value);
  const minutes = dayjs().diff(date, 'minute');
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = dayjs().diff(date, 'hour');
  if (hours < 24) return `${hours} 小时前`;
  const days = dayjs().diff(date, 'day');
  return days < 30 ? `${days} 天前` : date.format('YYYY-MM-DD');
}

export function formatBytes(value?: number | null) {
  if (value === undefined || value === null) return '—';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value / 1024;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size >= 10 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`;
}
