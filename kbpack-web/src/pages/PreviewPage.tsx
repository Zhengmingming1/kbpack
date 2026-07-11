import {
  ArrowLeftOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FullscreenOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Button, Select, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
import { getPackage, packageDownloadUrl } from '../api/packages';
import { createPreviewTicket, listDocuments, listVersions } from '../api/versions';

export function PreviewPage() {
  const { packageId = '', versionId = '' } = useParams();
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState(false);
  const [slow, setSlow] = useState(false);
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

  useEffect(() => {
    setLoaded(false);
    setSlow(false);
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
        {ticket.data?.preview_url ? (
          <iframe
            title={`${packageQuery.data?.title || '知识包'}原样预览`}
            src={ticket.data.preview_url}
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-downloads"
            referrerPolicy="no-referrer"
            onLoad={() => setLoaded(true)}
          />
        ) : null}
      </section>
    </main>
  );
}
