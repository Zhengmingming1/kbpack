import { ArrowLeftOutlined, EyeOutlined, LeftOutlined, MenuOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, List, Space, Typography } from 'antd';
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useNavigate, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
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
  const [tocOpen, setTocOpen] = useState(false);
  const documentQuery = useQuery({
    queryKey: ['document', documentId],
    queryFn: () => getDocument(documentId),
    enabled: Boolean(documentId),
  });

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

  return (
    <main className="reader-page">
      <header className="reader-toolbar">
        <Button type="text" icon={<ArrowLeftOutlined />} aria-label="返回" onClick={() => navigate(-1)} />
        <div className="reader-toolbar-title">
          <Typography.Text strong ellipsis>{data.title}</Typography.Text>
          <Typography.Text type="secondary">阅读模式</Typography.Text>
        </div>
        <Space size={4}>
          {data.package_id && data.version_id ? (
            <Button
              type="text"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/packages/${data.package_id}/preview/${data.version_id}`)}
            >
              <span className="desktop-only">原样预览</span>
            </Button>
          ) : null}
          <Button type="text" icon={<MenuOutlined />} aria-label="打开目录" onClick={() => setTocOpen(true)} />
        </Space>
      </header>

      <article className={`reader-content${isPlainText ? ' reader-content-text' : ''}`}>
        {isPlainText ? (
          <pre className="reader-plain-text">{data.content || ''}</pre>
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm, headingIdPlugin(data.heading_tree || [])]}
            components={{
              a: ({ href, children }) => (
                <a
                  href={resolvePackageAssetUrl(href, data.source_path, data.version_id)}
                  target="_blank"
                  rel="noreferrer"
                >
                  {children}
                </a>
              ),
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
            <List.Item
              className="toc-item"
              style={{ paddingInlineStart: Math.max(0, item.level - 1) * 14 }}
              onClick={() => {
                setTocOpen(false);
                document.getElementById(item.anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }}
            >
              {item.text}
            </List.Item>
          )}
        />
      </Drawer>
    </main>
  );
}
