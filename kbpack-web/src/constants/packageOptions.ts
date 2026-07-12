export const PACKAGE_SOURCE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'manual', label: '手工上传' },
  { value: 'ai', label: 'AI 生成' },
  { value: 'local', label: '本地导入' },
  { value: 'web_archive', label: '网页归档' },
];

export const PACKAGE_STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'draft', label: '草稿' },
  { value: 'active', label: '可用' },
  { value: 'deprecated', label: '已弃用' },
  { value: 'archived', label: '已归档' },
];

export const PACKAGE_VISIBILITY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'private', label: '私有' },
  { value: 'team', label: '团队可见' },
  { value: 'public', label: '公开' },
];

function optionLabel(
  options: ReadonlyArray<{ value: string; label: string }>,
  value?: string | null,
) {
  return options.find((option) => option.value === value)?.label || value || '';
}

export function packageSourceLabel(value?: string | null) {
  return optionLabel(PACKAGE_SOURCE_OPTIONS, value);
}

export function packageVisibilityLabel(value?: string | null) {
  return optionLabel(PACKAGE_VISIBILITY_OPTIONS, value);
}
