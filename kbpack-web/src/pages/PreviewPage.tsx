import {
  ArrowLeftOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FullscreenOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Button, Select, Space, Spin, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
import { getPackage, packageDownloadUrl } from '../api/packages';
import { createPreviewTicket, listDocuments, listVersions } from '../api/versions';

const PREVIEW_POSITION_STORAGE_PREFIX = 'kbpack:preview-position:v1';
const PREVIEW_POSITION_SCHEMA_VERSION = 1;
const PREVIEW_POSITION_SAVE_DELAY_MS = 250;

interface ScrollPosition {
  left: number;
  top: number;
}

interface PreviewFrameLocation {
  relativePath: string;
  search: string;
  hash: string;
}

interface PreviewFramePosition extends ScrollPosition {
  location?: PreviewFrameLocation;
}

interface PreviewPosition {
  schemaVersion: typeof PREVIEW_POSITION_SCHEMA_VERSION;
  outer: ScrollPosition;
  frame?: PreviewFramePosition;
  updatedAt: number;
}

function previewPositionStorageKey(packageId: string, versionId: string) {
  return PREVIEW_POSITION_STORAGE_PREFIX
    + ':'
    + encodeURIComponent(packageId)
    + ':'
    + encodeURIComponent(versionId);
}

function parseScrollPosition(value: unknown): ScrollPosition | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const candidate = value as Partial<ScrollPosition>;
  if (!Number.isFinite(candidate.left) || !Number.isFinite(candidate.top)) return undefined;
  return {
    left: Math.max(0, candidate.left as number),
    top: Math.max(0, candidate.top as number),
  };
}

function parseFrameLocation(value: unknown): PreviewFrameLocation | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const candidate = value as Partial<PreviewFrameLocation>;
  if (
    typeof candidate.relativePath !== 'string'
    || typeof candidate.search !== 'string'
    || typeof candidate.hash !== 'string'
    || candidate.relativePath.length > 4096
    || candidate.search.length > 4096
    || candidate.hash.length > 4096
  ) return undefined;
  return {
    relativePath: candidate.relativePath,
    search: candidate.search,
    hash: candidate.hash,
  };
}

function readPreviewPosition(storageKey: string): PreviewPosition | undefined {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return undefined;
    const candidate = JSON.parse(raw) as Partial<PreviewPosition>;
    if (candidate.schemaVersion !== PREVIEW_POSITION_SCHEMA_VERSION) return undefined;
    const outer = parseScrollPosition(candidate.outer);
    if (!outer) return undefined;
    const framePoint = parseScrollPosition(candidate.frame);
    const frameLocation = parseFrameLocation(candidate.frame?.location);
    return {
      schemaVersion: PREVIEW_POSITION_SCHEMA_VERSION,
      outer,
      frame: framePoint ? { ...framePoint, location: frameLocation } : undefined,
      updatedAt: typeof candidate.updatedAt === 'number' && Number.isFinite(candidate.updatedAt)
        ? candidate.updatedAt
        : 0,
    };
  } catch {
    return undefined;
  }
}

function writePreviewPosition(storageKey: string, position: PreviewPosition) {
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(position));
  } catch {
    // Storage can be unavailable or full. Previewing must continue without persistence.
  }
}

function previewPathPrefix(packageId: string, versionId: string) {
  return '/p/' + encodeURIComponent(packageId) + '/v/' + encodeURIComponent(versionId) + '/';
}

function frameLocationFromUrl(url: URL, packageId: string, versionId: string): PreviewFrameLocation | undefined {
  const prefix = previewPathPrefix(packageId, versionId);
  if (!url.pathname.startsWith(prefix)) return undefined;
  url.searchParams.delete('ticket');
  return {
    relativePath: url.pathname.slice(prefix.length),
    search: url.search,
    hash: url.hash,
  };
}

function restorePreviewUrl(
  previewUrl: string | undefined,
  savedLocation: PreviewFrameLocation | undefined,
  packageId: string,
  versionId: string,
) {
  if (!previewUrl || !savedLocation) return previewUrl;
  try {
    const issuedUrl = new URL(previewUrl, window.location.href);
    const prefix = previewPathPrefix(packageId, versionId);
    if (!issuedUrl.pathname.startsWith(prefix)) return previewUrl;

    const root = new URL(prefix, issuedUrl.origin);
    const restored = new URL(savedLocation.relativePath, root);
    if (restored.origin !== issuedUrl.origin || !restored.pathname.startsWith(prefix)) return previewUrl;

    const ticket = issuedUrl.searchParams.get('ticket');
    restored.search = savedLocation.search;
    if (ticket) restored.searchParams.set('ticket', ticket);
    restored.hash = savedLocation.hash;
    return restored.toString();
  } catch {
    return previewUrl;
  }
}

function sameFrameLocation(left?: PreviewFrameLocation, right?: PreviewFrameLocation) {
  return Boolean(
    left
    && right
    && left.relativePath === right.relativePath
    && left.search === right.search
    && left.hash === right.hash,
  );
}

function readFramePosition(
  iframe: HTMLIFrameElement | null,
  packageId: string,
  versionId: string,
): PreviewFramePosition | undefined {
  try {
    const frameWindow = iframe?.contentWindow;
    if (!frameWindow) return undefined;
    const location = frameLocationFromUrl(new URL(frameWindow.location.href), packageId, versionId);
    if (!location) return undefined;
    return {
      left: Math.max(0, frameWindow.scrollX),
      top: Math.max(0, frameWindow.scrollY),
      location,
    };
  } catch {
    // Cross-origin previews cannot expose their location or scroll offset; persistence degrades silently.
    return undefined;
  }
}

export function PreviewPage() {
  const { packageId = '', versionId = '' } = useParams();
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState(false);
  const [slow, setSlow] = useState(false);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const saveTimerRef = useRef<number>();
  const frameListenerCleanupRef = useRef<() => void>();
  const storageKey = previewPositionStorageKey(packageId, versionId);
  const packageQuery = useQuery({
    queryKey: ['package', packageId],
    queryFn: () => getPackage(packageId),
  });
  const versions = useQuery({
    queryKey: ['versions', packageId],
    queryFn: () => listVersions(packageId),
  });
  const documents = useQuery({
    queryKey: ['documents', versionId],
    queryFn: () => listDocuments(versionId),
    retry: false,
  });
  const ticket = useMutation({ mutationFn: () => createPreviewTicket(packageId, versionId) });
  const iframeSrc = useMemo(() => restorePreviewUrl(
    ticket.data?.preview_url,
    readPreviewPosition(storageKey)?.frame?.location,
    packageId,
    versionId,
  ), [packageId, storageKey, ticket.data?.preview_url, versionId]);

  const savePosition = useCallback(() => {
    const previous = readPreviewPosition(storageKey);
    const frame = readFramePosition(iframeRef.current, packageId, versionId);
    writePreviewPosition(storageKey, {
      schemaVersion: PREVIEW_POSITION_SCHEMA_VERSION,
      outer: {
        left: Math.max(0, window.scrollX),
        top: Math.max(0, window.scrollY),
      },
      frame: frame || previous?.frame,
      updatedAt: Date.now(),
    });
  }, [packageId, storageKey, versionId]);

  const scheduleSave = useCallback(() => {
    if (saveTimerRef.current !== undefined) return;
    saveTimerRef.current = window.setTimeout(() => {
      saveTimerRef.current = undefined;
      savePosition();
    }, PREVIEW_POSITION_SAVE_DELAY_MS);
  }, [savePosition]);

  const handleIframeLoad = useCallback(() => {
    setLoaded(true);
    frameListenerCleanupRef.current?.();
    frameListenerCleanupRef.current = undefined;

    const frameWindow = iframeRef.current?.contentWindow;
    if (!frameWindow) return;
    try {
      const current = readFramePosition(iframeRef.current, packageId, versionId);
      if (!current) return;
      const saved = readPreviewPosition(storageKey)?.frame;
      const shouldRestore = saved && (
        !saved.location || sameFrameLocation(saved.location, current.location)
      );
      let firstFrame = 0;
      let secondFrame = 0;
      if (shouldRestore) {
        firstFrame = window.requestAnimationFrame(() => {
          frameWindow.scrollTo(saved.left, saved.top);
          secondFrame = window.requestAnimationFrame(() => frameWindow.scrollTo(saved.left, saved.top));
        });
      }

      const onFrameScroll = () => scheduleSave();
      const onFramePageHide = () => savePosition();
      frameWindow.addEventListener('scroll', onFrameScroll, { passive: true });
      frameWindow.addEventListener('pagehide', onFramePageHide);
      frameListenerCleanupRef.current = () => {
        window.cancelAnimationFrame(firstFrame);
        window.cancelAnimationFrame(secondFrame);
        try {
          frameWindow.removeEventListener('scroll', onFrameScroll);
          frameWindow.removeEventListener('pagehide', onFramePageHide);
        } catch {
          // The iframe may have navigated across origins since the listeners were attached.
        }
      };
      if (!shouldRestore) savePosition();
    } catch {
      // A cross-origin preview cannot expose its inner scroll position to the app.
    }
  }, [packageId, savePosition, scheduleSave, storageKey, versionId]);

  useEffect(() => {
    const savedOuter = readPreviewPosition(storageKey)?.outer;
    const restoreFrame = savedOuter
      ? window.requestAnimationFrame(() => window.scrollTo(savedOuter.left, savedOuter.top))
      : 0;
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') savePosition();
    };
    window.addEventListener('scroll', scheduleSave, { passive: true });
    window.addEventListener('pagehide', savePosition);
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      window.cancelAnimationFrame(restoreFrame);
      window.removeEventListener('scroll', scheduleSave);
      window.removeEventListener('pagehide', savePosition);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      if (saveTimerRef.current !== undefined) {
        window.clearTimeout(saveTimerRef.current);
        saveTimerRef.current = undefined;
      }
      savePosition();
      frameListenerCleanupRef.current?.();
      frameListenerCleanupRef.current = undefined;
    };
  }, [savePosition, scheduleSave, storageKey]);

  useEffect(() => {
    setLoaded(false);
    setSlow(false);
    frameListenerCleanupRef.current?.();
    frameListenerCleanupRef.current = undefined;
    ticket.reset();
    const timer = window.setTimeout(() => ticket.mutate(), 0);
    return () => window.clearTimeout(timer);
    // The timeout lets React StrictMode discard its probe effect before a one-time ticket is issued.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [packageId, versionId]);

  useEffect(() => {
    if (!ticket.data?.preview_url || loaded) return;
    const timer = window.setTimeout(() => setSlow(true), 8000);
    return () => window.clearTimeout(timer);
  }, [loaded, ticket.data?.preview_url]);

  const refreshTicket = () => {
    savePosition();
    frameListenerCleanupRef.current?.();
    frameListenerCleanupRef.current = undefined;
    setLoaded(false);
    setSlow(false);
    ticket.reset();
    ticket.mutate();
  };

  return (
    <main className="preview-page">
      <header className="preview-toolbar">
        <Button type="text" icon={<ArrowLeftOutlined />} aria-label="返回详情" onClick={() => navigate(`/packages/${packageId}`)} />
        <div className="preview-title">
          <Typography.Text strong ellipsis>{packageQuery.data?.title || '知识包预览'}</Typography.Text>
          <Typography.Text type="secondary">原样预览</Typography.Text>
        </div>
        <Select
          className="preview-version-select"
          value={versionId}
          loading={versions.isPending}
          options={(versions.data || []).map((version) => ({ value: version.id, label: `v${version.version_no}${version.is_current ? ' · 当前' : ''}` }))}
          onChange={(nextVersion) => navigate(`/packages/${packageId}/preview/${nextVersion}`, { replace: true })}
        />
        <Space className="preview-actions" size={4}>
          <Button
            type="text"
            icon={<FileTextOutlined />}
            disabled={!documents.data?.length}
            onClick={() => documents.data?.[0] && navigate(`/documents/${documents.data[0].id}`)}
          >
            <span className="desktop-only">阅读模式</span>
          </Button>
          <Button type="text" icon={<ReloadOutlined />} aria-label="重新加载" onClick={refreshTicket} />
          <Button
            type="text"
            icon={<FullscreenOutlined />}
            aria-label="新窗口打开"
            disabled={!ticket.data?.preview_url}
            onClick={() => ticket.data?.preview_url && window.open(ticket.data.preview_url, '_blank', 'noopener,noreferrer')}
          />
          <Button type="text" icon={<DownloadOutlined />} aria-label="下载原始包" href={packageDownloadUrl(versionId)} />
        </Space>
      </header>

      <section className="preview-stage">
        {ticket.isPending ? (
          <div className="preview-state"><Spin size="large" /><Typography.Text type="secondary">正在获取安全预览凭证…</Typography.Text></div>
        ) : null}
        {ticket.isError ? (
          <div className="preview-state">
            <Alert
              type="error"
              showIcon
              message="无法打开原样预览"
              description={getApiErrorMessage(ticket.error, '版本不存在、尚未解析完成，或预览服务不可用。')}
              action={<Button onClick={refreshTicket}>重试</Button>}
            />
          </div>
        ) : null}
        {slow && !loaded ? (
          <Alert className="preview-slow-alert" type="info" showIcon message="预览仍在加载" description="知识包资源较多时可能需要更长时间，也可以尝试新窗口打开。" closable />
        ) : null}
        {iframeSrc ? (
          <iframe
            ref={iframeRef}
            title={`${packageQuery.data?.title || '知识包'}原样预览`}
            src={iframeSrc}
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
            referrerPolicy="no-referrer"
            onLoad={handleIframeLoad}
          />
        ) : null}
      </section>
    </main>
  );
}
