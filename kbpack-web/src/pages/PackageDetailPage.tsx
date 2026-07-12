import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileSearchOutlined,
  InboxOutlined,
  MoreOutlined,
  ReloadOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  AutoComplete,
  Button,
  Descriptions,
  Dropdown,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tree,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  archivePackage,
  deletePackage,
  getPackage,
  packageDownloadUrl,
  setFavorite,
  updatePackage,
  type PackageDetail,
} from '../api/packages';
import { getApiErrorMessage } from '../api/client';
import {
  deleteVersion,
  getFiles,
  getVersion,
  listDocuments,
  listVersions,
  reparseVersion,
  setCurrentVersion,
  type FileNode,
  type PackageVersion,
} from '../api/versions';
import { EmptyBlock, ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { StatusTag } from '../components/package/StatusTag';
import {
  PACKAGE_STATUS_OPTIONS,
  PACKAGE_VISIBILITY_OPTIONS,
  packageSourceLabel,
  packageVisibilityLabel,
} from '../constants/packageOptions';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { formatBytes, formatDate } from '../utils/format';

const REPARSE_POLL_TIMEOUT_MS = 5 * 60 * 1000;

function isParsing(status?: string | null) {
  const normalized = status?.toLowerCase();
  return normalized === 'pending' || normalized === 'processing';
}

function fileTreeData(nodes: FileNode[]): Array<{ key: string; title: string; isLeaf: boolean; children?: ReturnType<typeof fileTreeData> }> {
  return nodes.map((node) => ({
    key: node.path,
    title: node.type === 'file' && node.size ? `${node.path.split('/').pop()} · ${formatBytes(node.size)}` : node.path.split('/').pop() || node.path,
    isLeaf: node.type === 'file',
    children: node.children ? fileTreeData(node.children) : undefined,
  }));
}

function flatFiles(nodes: FileNode[]): FileNode[] {
  return nodes.flatMap((node) => [
    ...(node.type === 'file' ? [node] : []),
    ...flatFiles(node.children || []),
  ]);
}

function preferredEntryFile(nodes: FileNode[]) {
  const files = flatFiles(nodes);
  return files.find((file) => file.role === 'entry')?.path
    || files.find((file) => /(^|\/)index\.html?$/i.test(file.path))?.path
    || '';
}

export function PackageDetailPage() {
  const { packageId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [editOpen, setEditOpen] = useState(false);
  const [reparseTarget, setReparseTarget] = useState<PackageVersion>();
  const [reparseEntry, setReparseEntry] = useState('');
  const [reparseEntryTouched, setReparseEntryTouched] = useState(false);
  const [reparseWatch, setReparseWatch] = useState<{ versionId: string; startedAt: number }>();
  const skippedReparseVersionIds = useRef(new Set<string>());
  const [form] = Form.useForm();
  const isCompact = useMediaQuery('(max-width: 1023px)');

  const packageQuery = useQuery({
    queryKey: ['package', packageId],
    queryFn: () => getPackage(packageId),
    enabled: Boolean(packageId),
  });
  const versionsQuery = useQuery({
    queryKey: ['versions', packageId],
    queryFn: () => listVersions(packageId),
    enabled: Boolean(packageId),
  });
  const reparseStatusQuery = useQuery({
    queryKey: ['reparse-status', packageId, reparseWatch?.versionId, reparseWatch?.startedAt],
    queryFn: () => getVersion(packageId, reparseWatch!.versionId),
    enabled: Boolean(packageId && reparseWatch?.versionId),
    retry: 3,
    retryDelay: 1200,
    refetchInterval: (query) => (
      isParsing(query.state.data?.parse_status) && reparseWatch
        && Date.now() - reparseWatch.startedAt < REPARSE_POLL_TIMEOUT_MS
        ? 2000
        : false
    ),
  });
  const currentVersion =
    versionsQuery.data?.find((version) => version.is_current) ||
    versionsQuery.data?.find((version) => version.id === packageQuery.data?.current_version?.id) ||
    versionsQuery.data?.[0];
  const filesQuery = useQuery({
    queryKey: ['files', currentVersion?.id],
    queryFn: () => getFiles(currentVersion!.id),
    enabled: Boolean(currentVersion?.id),
    retry: false,
  });
  const documentsQuery = useQuery({
    queryKey: ['documents', currentVersion?.id],
    queryFn: () => listDocuments(currentVersion!.id),
    enabled: Boolean(currentVersion?.id),
    retry: false,
  });
  const reparseFilesQuery = useQuery({
    queryKey: ['files', reparseTarget?.id],
    queryFn: () => getFiles(reparseTarget!.id),
    enabled: Boolean(reparseTarget?.id),
    retry: false,
  });
  const reparseFileOptions = useMemo(() => {
    const files = flatFiles(reparseFilesQuery.data || []);
    return files
      .sort((left, right) => {
        const leftRank = left.role === 'entry' ? 0 : /\.html?$/i.test(left.path) ? 1 : 2;
        const rightRank = right.role === 'entry' ? 0 : /\.html?$/i.test(right.path) ? 1 : 2;
        return leftRank - rightRank || left.path.localeCompare(right.path);
      })
      .map((file) => ({ value: file.path, label: file.path }));
  }, [reparseFilesQuery.data]);

  useEffect(() => {
    if (reparseEntryTouched || reparseEntry || !reparseFilesQuery.data) return;
    setReparseEntry(preferredEntryFile(reparseFilesQuery.data));
  }, [reparseEntry, reparseEntryTouched, reparseFilesQuery.data]);

  useEffect(() => {
    if (reparseWatch) return;
    const pendingVersion = versionsQuery.data?.find((version) => (
      isParsing(version.parse_status) && !skippedReparseVersionIds.current.has(version.id)
    ));
    if (!pendingVersion) return;
    setReparseWatch((current) => current || {
      versionId: pendingVersion.id,
      startedAt: Date.now(),
    });
  }, [reparseWatch, versionsQuery.data]);

  useEffect(() => {
    if (!reparseWatch) return undefined;
    const remaining = REPARSE_POLL_TIMEOUT_MS - (Date.now() - reparseWatch.startedAt);
    const timeout = window.setTimeout(() => {
      skippedReparseVersionIds.current.add(reparseWatch.versionId);
      setReparseWatch(undefined);
      message.warning('重新解析仍在后台执行，请稍后手动刷新查看结果');
    }, Math.max(remaining, 0));
    return () => window.clearTimeout(timeout);
  }, [message, reparseWatch]);

  useEffect(() => {
    if (!reparseWatch) return;
    if (reparseStatusQuery.isError) {
      skippedReparseVersionIds.current.add(reparseWatch.versionId);
      setReparseWatch(undefined);
      message.warning('无法继续自动刷新解析进度，请稍后手动刷新');
      return;
    }

    const status = reparseStatusQuery.data?.parse_status?.toLowerCase();
    if (!status || isParsing(status)) return;

    const versionId = reparseWatch.versionId;
    queryClient.setQueryData<PackageVersion[]>(['versions', packageId], (versions) =>
      versions?.map((version) => (
        version.id === versionId ? { ...version, parse_status: status } : version
      )),
    );
    setReparseWatch(undefined);
    void Promise.all([
      queryClient.invalidateQueries({ queryKey: ['package', packageId] }),
      queryClient.invalidateQueries({ queryKey: ['versions', packageId] }),
      queryClient.invalidateQueries({ queryKey: ['files', versionId] }),
      queryClient.invalidateQueries({ queryKey: ['documents', versionId] }),
      queryClient.invalidateQueries({ queryKey: ['document'] }),
      queryClient.invalidateQueries({ queryKey: ['packages'] }),
    ]);
    if (status === 'success') message.success('重新解析已完成');
    else message.warning('重新解析已结束，但解析失败');
  }, [message, packageId, queryClient, reparseStatusQuery.data?.parse_status, reparseStatusQuery.isError, reparseWatch]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['package', packageId] });
    void queryClient.invalidateQueries({ queryKey: ['versions', packageId] });
    void queryClient.invalidateQueries({ queryKey: ['packages'] });
  };

  const actionMutation = useMutation({
    mutationFn: async (action: 'favorite' | 'archive' | 'delete') => {
      if (action === 'favorite') return setFavorite(packageId, !packageQuery.data?.is_favorite);
      if (action === 'archive') return archivePackage(packageId);
      return deletePackage(packageId);
    },
    onSuccess: (_, action) => {
      if (action === 'delete') {
        message.success('知识包已移入回收状态');
        navigate('/packages', { replace: true });
        return;
      }
      message.success(action === 'archive' ? '知识包已归档' : packageQuery.data?.is_favorite ? '已取消收藏' : '已收藏');
      refresh();
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const editMutation = useMutation({
    mutationFn: (values: Partial<PackageDetail>) => updatePackage(packageId, values),
    onSuccess: () => {
      message.success('知识包信息已更新');
      setEditOpen(false);
      refresh();
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const versionMutation = useMutation({
    mutationFn: async ({
      action,
      version,
      entryFile,
    }: {
      action: 'current' | 'delete' | 'reparse';
      version: PackageVersion;
      entryFile?: string;
    }) => {
      if (action === 'current') return setCurrentVersion(packageId, version.id);
      if (action === 'delete') return deleteVersion(packageId, version.id);
      return reparseVersion(version.id, entryFile);
    },
    onSuccess: (data, variables) => {
      message.success(
        variables.action === 'current'
          ? '当前版本已切换'
          : variables.action === 'delete'
            ? '版本已删除'
            : '已提交重新解析',
      );
      if (variables.action === 'reparse') {
        const parseStatus = data && typeof data === 'object' && 'parse_status' in data
          ? String(data.parse_status)
          : 'pending';
        queryClient.setQueryData<PackageVersion[]>(['versions', packageId], (versions) =>
          versions?.map((version) => (
            version.id === variables.version.id ? { ...version, parse_status: parseStatus } : version
          )),
        );
        skippedReparseVersionIds.current.delete(variables.version.id);
        setReparseWatch({ versionId: variables.version.id, startedAt: Date.now() });
        setReparseTarget(undefined);
        setReparseEntry('');
        setReparseEntryTouched(false);
      }
      refresh();
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const qualityNotes = useMemo<Array<{ type?: string; text?: string; source?: string }>>(() => {
    const notes = packageQuery.data?.quality_notes;
    if (Array.isArray(notes)) return notes;
    if (notes && typeof notes === 'object') {
      return Object.entries(notes).map(([type, value]) => ({ type, text: String(value) }));
    }
    return [];
  }, [packageQuery.data?.quality_notes]);

  const openReparse = (version: PackageVersion) => {
    const cachedFiles = queryClient.getQueryData<FileNode[]>(['files', version.id]);
    setReparseTarget(version);
    setReparseEntry(version.entry_file || preferredEntryFile(cachedFiles || []));
    setReparseEntryTouched(false);
  };

  if (packageQuery.isPending) return <LoadingBlock rows={10} />;
  if (packageQuery.isError) {
    return (
      <ErrorBlock
        title="无法打开知识包"
        description={getApiErrorMessage(packageQuery.error)}
        onRetry={() => void packageQuery.refetch()}
      />
    );
  }

  const pkg = packageQuery.data;
  const openEdit = () => {
    form.setFieldsValue({
      title: pkg.title,
      description: pkg.description,
      status: pkg.status,
      visibility: pkg.visibility,
    });
    setEditOpen(true);
  };

  const confirmDelete = () => {
    modal.confirm({
      title: '删除这个知识包？',
      content: '知识包会先进入软删除状态，相关版本将不再出现在常规列表中。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => actionMutation.mutateAsync('delete'),
    });
  };

  const chapterList = (
    <div className="chapter-list">
      {documentsQuery.isPending && currentVersion ? <LoadingBlock rows={5} /> : null}
      {documentsQuery.isError ? (
        <ErrorBlock description={getApiErrorMessage(documentsQuery.error)} onRetry={() => void documentsQuery.refetch()} />
      ) : null}
      {documentsQuery.data?.length === 0 ? <EmptyBlock title="当前版本没有抽取章节" /> : null}
      <List
        dataSource={documentsQuery.data || []}
        renderItem={(document) => (
          <List.Item className="chapter-row" onClick={() => navigate(`/documents/${document.id}`)}>
            <span className="chapter-number">{String(document.order_no).padStart(2, '0')}</span>
            <div>
              <Typography.Text strong>{document.title}</Typography.Text>
              <Typography.Text type="secondary">{document.word_count ? `${document.word_count} 字` : document.doc_type}</Typography.Text>
            </div>
          </List.Item>
        )}
      />
    </div>
  );

  const fileTree = (
    <div className="file-tree-panel">
      {filesQuery.isPending && currentVersion ? <LoadingBlock rows={6} /> : null}
      {filesQuery.isError ? (
        <ErrorBlock description={getApiErrorMessage(filesQuery.error)} onRetry={() => void filesQuery.refetch()} />
      ) : null}
      {filesQuery.data?.length === 0 ? <EmptyBlock title="当前版本没有文件记录" /> : null}
      {filesQuery.data?.length ? (
        <Tree showLine defaultExpandAll={filesQuery.data.length < 12} treeData={fileTreeData(filesQuery.data)} />
      ) : null}
    </div>
  );

  const versionColumns: TableColumnsType<PackageVersion> = [
    {
      title: '版本',
      width: 100,
      render: (_, version) => (
        <Space size={4}>
          <Typography.Text strong>v{version.version_no}</Typography.Text>
          {version.is_current || version.id === pkg.current_version?.id ? <span className="current-label">当前</span> : null}
        </Space>
      ),
    },
    { title: '状态', width: 110, render: (_, version) => <StatusTag status={version.parse_status} /> },
    { title: '文件', dataIndex: 'file_count', width: 70, render: (value) => value ?? '—' },
    { title: '大小', dataIndex: 'unpacked_size', width: 90, render: (value) => formatBytes(value) },
    { title: '上传时间', dataIndex: 'created_at', width: 150, render: (value) => formatDate(value) },
    {
      title: '操作',
      width: 120,
      align: 'right',
      render: (_, version) => (
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'preview', icon: <EyeOutlined />, label: '预览' },
              { key: 'download', icon: <DownloadOutlined />, label: '下载' },
              { key: 'current', label: '设为当前版本', disabled: version.is_current || version.id === pkg.current_version?.id },
              {
                key: 'reparse',
                icon: <ReloadOutlined />,
                label: '重新解析',
                disabled: Boolean(reparseWatch) || isParsing(version.parse_status),
              },
              { type: 'divider' },
              { key: 'delete', icon: <DeleteOutlined />, label: '删除版本', danger: true, disabled: version.is_current || version.id === pkg.current_version?.id },
            ],
            onClick: ({ key }) => {
              if (key === 'preview') navigate(`/packages/${packageId}/preview/${version.id}`);
              else if (key === 'download') window.location.assign(packageDownloadUrl(version.id));
              else if (key === 'current') versionMutation.mutate({ action: 'current', version });
              else if (key === 'reparse') openReparse(version);
              else if (key === 'delete') {
                modal.confirm({
                  title: `删除 v${version.version_no}？`,
                  content: '此操作会删除该版本的文件、抽取内容和索引记录。',
                  okText: '删除版本',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () => versionMutation.mutateAsync({ action: 'delete', version }),
                });
              }
            },
          }}
        >
          <Button type="text" icon={<MoreOutlined />} aria-label={`管理 v${version.version_no}`} />
        </Dropdown>
      ),
    },
  ];

  const versionsTable = versionsQuery.isError ? (
    <ErrorBlock description={getApiErrorMessage(versionsQuery.error)} onRetry={() => void versionsQuery.refetch()} />
  ) : (
    <Table
      rowKey="id"
      columns={versionColumns}
      dataSource={versionsQuery.data || []}
      loading={versionsQuery.isPending}
      pagination={false}
      scroll={{ x: 720 }}
    />
  );

  const overview = (
    <div className="detail-overview">
      <Typography.Paragraph className="detail-description">
        {pkg.description || '这个知识包还没有填写描述。'}
      </Typography.Paragraph>
      <div className="fact-grid">
        <div><span>章节数</span><strong>{documentsQuery.data?.length ?? '—'}</strong></div>
        <div><span>文件数</span><strong>{currentVersion?.file_count ?? pkg.file_count ?? '—'}</strong></div>
        <div><span>版本数</span><strong>{pkg.versions_count ?? versionsQuery.data?.length ?? '—'}</strong></div>
        <div><span>解压大小</span><strong>{formatBytes(currentVersion?.unpacked_size ?? pkg.unpacked_size)}</strong></div>
      </div>
      <div className="section-heading"><h3>章节列表</h3></div>
      {chapterList}
    </div>
  );

  const quality = qualityNotes.length ? (
    <div className="quality-list">
      {qualityNotes.map((note, index) => (
        <article key={`${note.type || 'note'}-${index}`}>
          <StatusTag status={note.type || 'note'} />
          <Typography.Text strong>{note.text || '质量记录'}</Typography.Text>
          {note.source ? <Typography.Text type="secondary">来源：{note.source}</Typography.Text> : null}
        </article>
      ))}
    </div>
  ) : (
    <EmptyBlock title="暂无质量记录" description="解析流水线没有报告需要复核的问题。" />
  );

  const desktopContent = (
    <Tabs
      defaultActiveKey="overview"
      items={[
        { key: 'overview', label: '概览', children: overview },
        { key: 'content', label: '抽取内容', children: chapterList },
        { key: 'versions', label: '版本历史', children: versionsTable },
        { key: 'quality', label: '质量信息', children: quality },
      ]}
    />
  );

  const mobileContent = (
    <Tabs
      className="mobile-detail-tabs"
      defaultActiveKey="overview"
      items={[
        { key: 'overview', label: '概览', children: overview },
        { key: 'chapters', label: '章节', children: chapterList },
        { key: 'versions', label: '版本', children: versionsTable },
        { key: 'files', label: '文件', children: fileTree },
      ]}
    />
  );

  return (
    <div className="package-detail-page">
      <Button className="back-button" type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/packages')}>
        返回知识包
      </Button>
      <header className="detail-header">
        <div className="detail-title">
          <Typography.Title level={1}>{pkg.title}</Typography.Title>
          <div className="detail-title-meta">
            <StatusTag status={pkg.status} />
            {(pkg.tags || []).map((tag) => <span className="text-tag" key={tag}>{tag}</span>)}
            <Typography.Text type="secondary">更新于 {formatDate(pkg.updated_at)}</Typography.Text>
          </div>
        </div>
        <Space wrap className="detail-actions">
          <Button
            type="primary"
            icon={<EyeOutlined />}
            disabled={!currentVersion}
            onClick={() => currentVersion && navigate(`/packages/${packageId}/preview/${currentVersion.id}`)}
          >
            预览
          </Button>
          <Button icon={<FileSearchOutlined />} onClick={() => navigate(`/search?package_id=${encodeURIComponent(packageId)}`)}>
            <span className="desktop-only">搜索本包</span>
          </Button>
          <Button
            icon={<CloudUploadOutlined />}
            onClick={() => navigate(`/packages/upload?packageId=${encodeURIComponent(packageId)}`)}
          >
            <span className="desktop-only">上传新版本</span>
          </Button>
          <Dropdown
            menu={{
              items: [
                { key: 'favorite', icon: pkg.is_favorite ? <StarFilled /> : <StarOutlined />, label: pkg.is_favorite ? '取消收藏' : '收藏' },
                { key: 'edit', icon: <EditOutlined />, label: '编辑信息' },
                { key: 'archive', icon: <InboxOutlined />, label: '归档' },
                { type: 'divider' },
                { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true },
              ],
              onClick: ({ key }) => {
                if (key === 'favorite') actionMutation.mutate('favorite');
                if (key === 'edit') openEdit();
                if (key === 'archive') actionMutation.mutate('archive');
                if (key === 'delete') confirmDelete();
              },
            }}
          >
            <Button icon={<MoreOutlined />} aria-label="更多操作" />
          </Dropdown>
        </Space>
      </header>

      {isCompact ? mobileContent : (
        <div className="detail-layout">
          <aside className="detail-left-column">
            <section className="surface surface-pad">
              <div className="section-heading"><h3>章节目录</h3></div>
              {chapterList}
            </section>
            <section className="surface surface-pad">
              <div className="section-heading"><h3>文件树</h3></div>
              {fileTree}
            </section>
          </aside>
          <div className="detail-center-column surface surface-pad">{desktopContent}</div>
          <aside className="detail-right-column">
            <section className="surface surface-pad">
              <div className="section-heading"><h3>元数据</h3></div>
              <Descriptions column={1} size="small" colon={false}>
                <Descriptions.Item label="来源">{pkg.source_name || packageSourceLabel(pkg.source_type) || '手工上传'}</Descriptions.Item>
                <Descriptions.Item label="可见性">{packageVisibilityLabel(pkg.visibility)}</Descriptions.Item>
                <Descriptions.Item label="创建时间">{formatDate(pkg.created_at)}</Descriptions.Item>
                <Descriptions.Item label="知识包 ID"><Typography.Text copyable>{pkg.id}</Typography.Text></Descriptions.Item>
              </Descriptions>
            </section>
            <section className="surface surface-pad">
              <div className="section-heading"><h3>当前版本</h3></div>
              {currentVersion ? (
                <Descriptions column={1} size="small" colon={false}>
                  <Descriptions.Item label="版本">v{currentVersion.version_no}</Descriptions.Item>
                  <Descriptions.Item label="状态"><StatusTag status={currentVersion.parse_status} /></Descriptions.Item>
                  <Descriptions.Item label="原始文件">{currentVersion.original_filename}</Descriptions.Item>
                  <Descriptions.Item label="存储">{formatBytes(currentVersion.unpacked_size)}</Descriptions.Item>
                </Descriptions>
              ) : <EmptyBlock title="暂无版本" />}
            </section>
          </aside>
        </div>
      )}

      <Modal
        title="编辑知识包信息"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={editMutation.isPending}
        okText="保存"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" onFinish={(values) => editMutation.mutate(values)}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={4} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={PACKAGE_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="visibility" label="可见性">
            <Select options={PACKAGE_VISIBILITY_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={reparseTarget ? `重新解析 v${reparseTarget.version_no}` : '重新解析'}
        open={Boolean(reparseTarget)}
        onCancel={() => {
          setReparseTarget(undefined);
          setReparseEntry('');
          setReparseEntryTouched(false);
        }}
        onOk={() => {
          if (!reparseTarget) return;
          versionMutation.mutate({
            action: 'reparse',
            version: reparseTarget,
            entryFile: reparseEntry.trim() || undefined,
          });
        }}
        confirmLoading={versionMutation.isPending && versionMutation.variables?.action === 'reparse'}
        okText="提交重新解析"
        cancelText="取消"
      >
        <Typography.Paragraph type="secondary">
          选择或输入该版本的入口文件路径。留空时沿用当前入口文件。
        </Typography.Paragraph>
        <Form layout="vertical">
          <Form.Item label="入口文件">
            <AutoComplete
              value={reparseEntry}
              options={reparseFileOptions}
              placeholder="例如 index.html"
              onChange={(value) => {
                setReparseEntry(value);
                setReparseEntryTouched(true);
              }}
              filterOption={(input, option) =>
                String(option?.value || '').toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>
        </Form>
        {reparseFilesQuery.isPending ? (
          <Typography.Text type="secondary">正在读取该版本的文件清单...</Typography.Text>
        ) : null}
        {reparseFilesQuery.isError ? (
          <Typography.Text type="danger">文件清单加载失败，仍可手工输入路径。</Typography.Text>
        ) : null}
      </Modal>
    </div>
  );
}
