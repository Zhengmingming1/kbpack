const SPECIAL_URL = /^(?:\/\/|#|\?)/;
const UNSAFE_SCHEMES = new Set(['javascript', 'vbscript', 'file']);

function schemeOf(value: string) {
  const colonIndex = value.indexOf(':');
  if (colonIndex < 0) return undefined;
  const candidate = value
    .slice(0, colonIndex)
    .replace(/[\u0000-\u0020]/g, '')
    .toLowerCase();
  return /^[a-z][a-z\d+.-]*$/.test(candidate) ? candidate : undefined;
}

export function resolvePackageAssetUrl(
  value: string | undefined,
  sourcePath: string | undefined,
  versionId: string | undefined,
) {
  if (!value || !versionId) return value;

  const normalizedValue = value.trimStart();
  const scheme = schemeOf(normalizedValue);
  if (scheme) return UNSAFE_SCHEMES.has(scheme) ? undefined : value;
  if (SPECIAL_URL.test(normalizedValue)) return value;

  try {
    const normalizedSourcePath = (sourcePath || '')
      .replace(/\\/g, '/')
      .replace(/^\/+/, '');
    const encodedSourcePath = normalizedSourcePath
      .split('/')
      .map((segment) => encodeURIComponent(segment))
      .join('/');
    const baseUrl = new URL(encodedSourcePath || '_document.md', 'https://kbpack.invalid/');
    const resolved = new URL(normalizedValue.replace(/\\/g, '/'), baseUrl);
    const assetPath = resolved.pathname.replace(/^\/+/, '');

    return `/api/v1/versions/${encodeURIComponent(versionId)}/assets/${assetPath}${resolved.search}${resolved.hash}`;
  } catch {
    return value;
  }
}
