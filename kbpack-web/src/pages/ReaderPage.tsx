import {
  ArrowLeftOutlined,
  BookFilled,
  BookOutlined,
  EyeOutlined,
  LeftOutlined,
  MenuOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Drawer, List, Space, Tooltip, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
import {
  getDocumentReadingState,
  readingItemBookmarked,
  updateDocumentBookmark,
  updateDocumentProgress,
} from '../api/reading';
import { getDocument, type DocumentDetail } from '../api/versions';
import { ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { resolvePackageAssetUrl } from '../utils/packageAssetUrl';

interface MarkdownAstNode {
  type: string;
  children?: MarkdownAstNode[];
  data?: {
    hProperties?: Record<string, unknown>;
    [key: string]: unknown;
  };
}

function headingIdPlugin(headings: NonNullable<DocumentDetail['heading_tree']>) {
  return () => (tree: MarkdownAstNode) => {
    let headingIndex = 0;

    const visit = (node: MarkdownAstNode) => {
      if (node.type === 'heading') {
        const heading = headings[headingIndex++];
        if (heading) {
          node.data = {
            ...node.data,
            hProperties: {
              ...node.data?.hProperties,
              id: heading.anchor,
            },
          };
        }
      }
      node.children?.forEach(visit);
    };

    visit(tree);
  };
}

export function ReaderPage() {
  const { documentId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [tocOpen, setTocOpen] = useState(false);
  const [readingProgress, setReadingProgress] = useState(0);
  const progressTimerRef = useRef<number>();
  const latestProgressRef = useRef(0);
  const lastSavedProgressRef = useRef<number>();
  const restoredDocumentRef = useRef('');
  const documentQuery = useQuery({
    queryKey: ['document', documentId],
    queryFn: () => getDocument(documentId),
    enabled: Boolean(documentId),
  });
  const readingState = useQuery({
    queryKey: ['reading', 'state', documentId],
    queryFn: () => getDocumentReadingState(documentId),
    enabled: Boolean(documentId),
    retry: false,
  });
  const bookmarkMutation = useMutation({
    mutationFn: (bookmarked: boolean) => updateDocumentBookmark(documentId, bookmarked),
    onSuccess: (data) => {
      queryClient.setQueryData(['reading', 'state', documentId], data);
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['reading', 'bookmarks'] }),
        queryClient.invalidateQueries({ queryKey: ['reading', 'recent'] }),
      ]);
    },
    onError: (error) => message.error(getApiErrorMessage(error, '书签更新失败')),
  });

  useEffect(() => {
    restoredDocumentRef.current = '';
    lastSavedProgressRef.current = undefined;
    latestProgressRef.current = 0;
    setReadingProgress(0);
  }, [documentId]);

  useEffect(() => {
    if (!readingState.data) return;
    const progress = Math.min(100, Math.max(0, Math.round(readingState.data.progress || 0)));
    latestProgressRef.current = progress;
    lastSavedProgressRef.current = progress;
    setReadingProgress(progress);
  }, [readingState.data]);

  useEffect(() => {
    const restoreKey = `${documentId}:${location.hash}`;
    if (!documentQuery.data || readingState.isPending || restoredDocumentRef.current === restoreKey) return undefined;
    restoredDocumentRef.current = restoreKey;
    let frame = 0;
    let secondFrame = 0;
    frame = window.requestAnimationFrame(() => {
      const rawHash = location.hash.replace(/^#/, '');
      if (rawHash) {
        let anchor = rawHash;
        try {
          anchor = decodeURIComponent(rawHash);
        } catch {
          // Keep the original hash when it is not valid URI encoding.
        }
        document.getElementById(anchor)?.scrollIntoView({ block: 'start' });
        return;
      }
      const progress = Math.min(100, Math.max(0, readingState.data?.progress || 0));
      if (progress <= 0) {
        window.scrollTo({ top: 0 });
        return;
      }
      secondFrame = window.requestAnimationFrame(() => {
        const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
        window.scrollTo({ top: maxScroll * (progress / 100) });
      });
    });
    return () => {
      window.cancelAnimationFrame(frame);
      window.cancelAnimationFrame(secondFrame);
    };
  }, [documentId, documentQuery.data, location.hash, readingState.data?.progress, readingState.isPending]);

  const flushProgress = useCallback(() => {
    const progress = latestProgressRef.current;
    if (!documentId || !documentQuery.data || progress === lastSavedProgressRef.current) return;
    lastSavedProgressRef.current = progress;
    void updateDocumentProgress(documentId, progress)
      .then((data) => {
        queryClient.setQueryData(['reading', 'state', documentId], data);
        void queryClient.invalidateQueries({ queryKey: ['reading', 'recent'] });
      })
      .catch(() => {
        lastSavedProgressRef.current = undefined;
      });
  }, [documentId, documentQuery.data, queryClient]);

  useEffect(() => {
    if (!documentQuery.data) return undefined;
    const updateProgress = () => {
      const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
      const progress = maxScroll > 0 ? Math.round((window.scrollY / maxScroll) * 100) : 100;
      const normalized = Math.min(100, Math.max(0, progress));
      latestProgressRef.current = normalized;
      setReadingProgress((current) => (current === normalized ? current : normalized));
      if (progressTimerRef.current !== undefined) window.clearTimeout(progressTimerRef.current);
      progressTimerRef.current = window.setTimeout(flushProgress, 900);
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') flushProgress();
    };
    updateProgress();
    window.addEventListener('scroll', updateProgress, { passive: true });
    window.addEventListener('pagehide', flushProgress);
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      window.removeEventListener('scroll', updateProgress);
      window.removeEventListener('pagehide', flushProgress);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      if (progressTimerRef.current !== undefined) {
        window.clearTimeout(progressTimerRef.current);
        progressTimerRef.current = undefined;
      }
      flushProgress();
    };
  }, [documentQuery.data, flushProgress]);

  if (documentQuery.isPending) {
    return <main className="reader-page"><LoadingBlock rows={12} /></main>;
  }
  if (documentQuery.isError) {
    return (
      <main className="reader-page reader-error">
        <ErrorBlock title="无法打开章节" description={getApiErrorMessage(documentQuery.error)} onRetry={() => void documentQuery.refetch()} />
      </main>
    );
  }

  const data = documentQuery.data;
  const isPlainText = data.doc_type === 'text';
  const bookmarked = readingItemBookmarked(readingState.data);

  return (
    <main className="reader-page">
      <header className="reader-toolbar">
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          aria-label="返回"
          onClick={() => {
            if (location.key === 'default' && data.package_id) navigate(`/packages/${data.package_id}`);
            else navigate(-1);
          }}
        />
        <div className="reader-toolbar-title">
          <Typography.Text strong ellipsis>{data.title}</Typography.Text>
          <Typography.Text type="secondary">阅读模式</Typography.Text>
        </div>
        <Space size={4}>
          {data.package_id && data.version_id ? (
            <Button
              type="text"
              icon={<EyeOutlined />}
              aria-label="原样预览"
              onClick={() => navigate(`/packages/${data.package_id}/preview/${data.version_id}`)}
            >
              <span className="desktop-only">原样预览</span>
            </Button>
          ) : null}
          <Tooltip title={bookmarked ? '取消书签' : '添加书签'}>
            <Button
              type="text"
              icon={bookmarked ? <BookFilled /> : <BookOutlined />}
              aria-label={bookmarked ? '取消书签' : '添加书签'}
              loading={readingState.isPending || bookmarkMutation.isPending}
              onClick={() => bookmarkMutation.mutate(!bookmarked)}
            />
          </Tooltip>
          <Button type="text" icon={<MenuOutlined />} aria-label="打开目录" onClick={() => setTocOpen(true)} />
        </Space>
      </header>

      <div
        className="reader-progress-track"
        role="progressbar"
        aria-label="阅读进度"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={readingProgress}
      >
        <span style={{ width: `${readingProgress}%` }} />
      </div>

      <article className={`reader-content${isPlainText ? ' reader-content-text' : ''}`}>
        {isPlainText ? (
          <pre className="reader-plain-text">{data.content || ''}</pre>
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm, headingIdPlugin(data.heading_tree || [])]}
            components={{
              a: ({ href, children }) => {
                const inPageAnchor = href?.trimStart().startsWith('#');
                return (
                  <a
                    href={resolvePackageAssetUrl(href, data.source_path, data.version_id)}
                    target={inPageAnchor ? undefined : '_blank'}
                    rel={inPageAnchor ? undefined : 'noreferrer'}
                  >
                    {children}
                  </a>
                );
              },
              img: ({ src, alt }) => (
                <img
                  src={resolvePackageAssetUrl(src, data.source_path, data.version_id)}
                  alt={alt || ''}
                  loading="lazy"
                  referrerPolicy="no-referrer"
                />
              ),
            }}
          >
            {data.content || ''}
          </ReactMarkdown>
        )}
      </article>

      <footer className="reader-footer">
        <Button
          icon={<LeftOutlined />}
          disabled={!data.prev_document_id}
          onClick={() => data.prev_document_id && navigate(`/documents/${data.prev_document_id}`)}
        >
          上一章
        </Button>
        <Button
          type="primary"
          disabled={!data.next_document_id}
          onClick={() => data.next_document_id && navigate(`/documents/${data.next_document_id}`)}
        >
          下一章 <RightOutlined />
        </Button>
      </footer>

      <Drawer title="章节目录" placement="right" open={tocOpen} onClose={() => setTocOpen(false)}>
        <List
          dataSource={data.heading_tree || []}
          locale={{ emptyText: '本章没有可用目录' }}
          renderItem={(item) => (
            <List.Item>
              <button
                type="button"
                className="toc-item"
                style={{ paddingInlineStart: Math.max(0, item.level - 1) * 14 }}
                onClick={() => {
                  setTocOpen(false);
                  document.getElementById(item.anchor)?.scrollIntoView({
                    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
                    block: 'start',
                  });
                }}
              >
                {item.text}
              </button>
            </List.Item>
          )}
        />
      </Drawer>
    </main>
  );
}
