import { Tag } from 'antd';

const statusMap: Record<string, { color: string; label: string }> = {
  active: { color: 'success', label: '可用' },
  draft: { color: 'default', label: '草稿' },
  pending: { color: 'gold', label: '待解析' },
  processing: { color: 'processing', label: '解析中' },
  success: { color: 'success', label: '解析成功' },
  up: { color: 'success', label: '正常' },
  down: { color: 'error', label: '异常' },
  failed: { color: 'error', label: '解析失败' },
  archived: { color: 'default', label: '已归档' },
  deprecated: { color: 'orange', label: '已过期' },
};

export function StatusTag({ status }: { status?: string | null }) {
  const normalized = status?.toLowerCase() || 'unknown';
  const config = statusMap[normalized] || { color: 'default', label: status || '未知' };
  return <Tag color={config.color}>{config.label}</Tag>;
}
