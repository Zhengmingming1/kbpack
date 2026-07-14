import { SwapOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Collapse,
  Empty,
  Modal,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { getApiErrorMessage } from '../../api/client';
import {
  getVersionDiff,
  type PackageVersion,
  type VersionDiffAssetChange,
  type VersionDiffAssetMetadata,
  type VersionDiffChangeType,
  type VersionDiffDocumentChange,
  type VersionDiffMetadataChange,
} from '../../api/versions';
import { formatBytes } from '../../utils/format';

interface VersionDiffModalProps {
  open: boolean;
  packageId: string;
  versions: PackageVersion[];
  initialBaseVersionId?: string;
  initialTargetVersionId?: string;
  onClose: () => void;
}

type ChangeFilter = 'all' | 'modified' | 'added' | 'removed';

const metadataLabels: Record<string, string> = {
  version_no: '版本号',
  original_filename: '原始文件',
  content_hash: '内容哈希',
  entry_file: '入口文件',
  unpacked_size: '解压大小',
  file_count: '文件数',
  parse_status: '解析状态',
};

const documentFieldLabels: Record<string, string> = {
  document: '文档',
  content: '内容',
  title: '标题',
  doc_type: '类型',
  order_no: '顺序',
  word_count: '字数',
};

const assetFieldLabels: Record<string, string> = {
  mime_type: '类型',
  size: '大小',
  sha256: '哈希',
  role: '角色',
};

function changeLabel(changeType: VersionDiffChangeType) {
  if (changeType === 'added') return '新增';
  if (changeType === 'removed') return '删除';
  if (changeType === 'modified') return '修改';
  return '未变化';
}

function changeColor(changeType: VersionDiffChangeType) {
  if (changeType === 'added') return 'green';
  if (changeType === 'removed') return 'red';
  if (changeType === 'modified') return 'gold';
  return 'default';
}

function renderValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return <Typography.Text type="secondary">无</Typography.Text>;
  }
  const rendered = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return <Typography.Text className="version-diff-value">{rendered}</Typography.Text>;
}

function documentDescription(change: VersionDiffDocumentChange) {
  const metadata = change.after || change.before;
  const facts = [
    metadata?.title,
    metadata?.doc_type,
    metadata?.word_count === null || metadata?.word_count === undefined
      ? undefined
      : `${metadata.word_count} 字`,
  ].filter(Boolean);
  return facts.join(' · ');
}

function renderAssetMetadata(metadata?: VersionDiffAssetMetadata | null) {
  if (!metadata) return <Typography.Text type="secondary">无</Typography.Text>;
  const hash = metadata.sha256 || '';
  return (
    <div className="version-diff-asset-meta">
      <Typography.Text>{formatBytes(metadata.size)}</Typography.Text>
      <Typography.Text type="secondary">{metadata.mime_type || metadata.role || '文件'}</Typography.Text>
      <Typography.Text code title={hash}>{hash.length > 22 ? `${hash.slice(0, 22)}…` : hash}</Typography.Text>
    </div>
  );
}

export function VersionDiffModal({
  open,
  packageId,
  versions,
  initialBaseVersionId,
  initialTargetVersionId,
  onClose,
}: VersionDiffModalProps) {
  const [baseVersionId, setBaseVersionId] = useState('');
  const [targetVersionId, setTargetVersionId] = useState('');
  const [filter, setFilter] = useState<ChangeFilter>('all');

  useEffect(() => {
    if (!open) return;
    setBaseVersionId(initialBaseVersionId || versions[1]?.id || versions[0]?.id || '');
    setTargetVersionId(initialTargetVersionId || versions[0]?.id || versions[1]?.id || '');
    setFilter('all');
  }, [initialBaseVersionId, initialTargetVersionId, open, versions]);

  const canCompare = Boolean(
    packageId && baseVersionId && targetVersionId && baseVersionId !== targetVersionId,
  );
  const diffQuery = useQuery({
    queryKey: ['version-diff', packageId, baseVersionId, targetVersionId],
    queryFn: () => getVersionDiff(packageId, baseVersionId, targetVersionId),
    enabled: open && canCompare,
    retry: false,
  });

  const versionOptions = versions.map((version) => ({
    value: version.id,
    label: `v${version.version_no}${version.is_current ? ' · 当前' : ''}`,
  }));
  const summary = diffQuery.data?.document_summary;
  const filteredChanges = useMemo(() => {
    const changes = diffQuery.data?.document_changes || [];
    return filter === 'all' ? changes : changes.filter((change) => change.change_type === filter);
  }, [diffQuery.data?.document_changes, filter]);

  const metadataColumns: TableColumnsType<VersionDiffMetadataChange> = [
    {
      title: '字段',
      dataIndex: 'field',
      width: 130,
      render: (field: string) => metadataLabels[field] || field,
    },
    {
      title: '变化',
      dataIndex: 'change_type',
      width: 86,
      render: (changeType: VersionDiffChangeType) => (
        <Tag color={changeColor(changeType)}>{changeLabel(changeType)}</Tag>
      ),
    },
    { title: '基线版本', dataIndex: 'before', render: renderValue },
    { title: '目标版本', dataIndex: 'after', render: renderValue },
  ];

  const assetColumns: TableColumnsType<VersionDiffAssetChange> = [
    {
      title: '文件',
      dataIndex: 'path',
      width: 240,
      render: (path: string) => <Typography.Text className="version-diff-asset-path">{path}</Typography.Text>,
    },
    {
      title: '变化',
      dataIndex: 'change_type',
      width: 86,
      render: (changeType: VersionDiffChangeType) => (
        <Tag color={changeColor(changeType)}>{changeLabel(changeType)}</Tag>
      ),
    },
    {
      title: '变化字段',
      dataIndex: 'fields_changed',
      width: 150,
      render: (fields: string[]) => fields.map((field) => assetFieldLabels[field] || field).join('、'),
    },
    { title: '基线版本', dataIndex: 'before', render: renderAssetMetadata },
    { title: '目标版本', dataIndex: 'after', render: renderAssetMetadata },
  ];

  const documentItems = filteredChanges.map((change) => ({
    key: change.source_path,
    label: (
      <div className="version-diff-document-heading">
        <span>
          <Typography.Text strong>{change.source_path}</Typography.Text>
          {documentDescription(change) ? (
            <Typography.Text type="secondary">{documentDescription(change)}</Typography.Text>
          ) : null}
        </span>
        <Tag color={changeColor(change.change_type)}>{changeLabel(change.change_type)}</Tag>
      </div>
    ),
    children: (
      <div className="version-diff-document">
        <Space wrap size={[6, 6]} className="version-diff-fields">
          {change.fields_changed.map((field) => (
            <Tag key={field}>{documentFieldLabels[field] || field}</Tag>
          ))}
        </Space>
        {change.content_diff?.hunks.map((hunk, hunkIndex) => (
          <div
            className="version-diff-hunk"
            key={`${change.source_path}-${hunk.old_start}-${hunk.new_start}-${hunkIndex}`}
          >
            <div className="version-diff-hunk-header">
              @@ -{hunk.old_start},{hunk.old_count} +{hunk.new_start},{hunk.new_count} @@
            </div>
            {hunk.lines.map((line, lineIndex) => (
              <div
                className={`version-diff-line is-${line.type}`}
                key={`${hunkIndex}-${line.old_line ?? 'x'}-${line.new_line ?? 'x'}-${lineIndex}`}
              >
                <span>{line.old_line ?? ''}</span>
                <span>{line.new_line ?? ''}</span>
                <span>{line.type === 'added' ? '+' : line.type === 'removed' ? '-' : ' '}</span>
                <code>{line.text || ' '}</code>
              </div>
            ))}
          </div>
        ))}
        {change.content_diff?.truncated ? (
          <Alert type="warning" showIcon message="此文档的内容差异已按安全上限截断" />
        ) : null}
        {change.content_diff?.status === 'omitted_limit' ? (
          <Alert type="info" showIcon message="详细内容差异已省略，文档摘要仍完整保留" />
        ) : null}
        {change.content_diff?.status === 'unavailable' ? (
          <Alert type="warning" showIcon message="无法读取此文档的内容预览" />
        ) : null}
      </div>
    ),
  }));

  return (
    <Modal
      title="版本 Diff"
      open={open}
      onCancel={onClose}
      footer={<Button onClick={onClose}>关闭</Button>}
      width={1120}
      destroyOnHidden
    >
      <div className="version-diff-controls">
        <label>
          <span>基线版本</span>
          <Select
            value={baseVersionId || undefined}
            options={versionOptions}
            onChange={setBaseVersionId}
          />
        </label>
        <Button
          icon={<SwapOutlined />}
          aria-label="交换基线版本和目标版本"
          disabled={!baseVersionId || !targetVersionId}
          onClick={() => {
            setBaseVersionId(targetVersionId);
            setTargetVersionId(baseVersionId);
          }}
        />
        <label>
          <span>目标版本</span>
          <Select
            value={targetVersionId || undefined}
            options={versionOptions}
            onChange={setTargetVersionId}
          />
        </label>
      </div>

      {!canCompare ? <Alert type="info" showIcon message="请选择两个不同的版本" /> : null}
      {canCompare && diffQuery.isPending ? (
        <div className="version-diff-loading">
          <Spin />
          <Typography.Text type="secondary">正在计算版本差异…</Typography.Text>
        </div>
      ) : null}
      {diffQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message="版本对比失败"
          description={getApiErrorMessage(diffQuery.error)}
          action={<Button onClick={() => void diffQuery.refetch()}>重试</Button>}
        />
      ) : null}
      {diffQuery.data ? (
        <div className="version-diff-result">
          {diffQuery.data.truncated ? (
            <Alert
              type="warning"
              showIcon
              message="差异结果已按安全上限截断"
              description="统计覆盖已比较的文档，部分大文档只显示摘要或有限的差异行。"
            />
          ) : null}
          <div className="version-diff-summary">
            <div><span>新增</span><strong className="is-added">{summary?.added ?? 0}</strong></div>
            <div><span>删除</span><strong className="is-removed">{summary?.removed ?? 0}</strong></div>
            <div><span>修改</span><strong className="is-modified">{summary?.modified ?? 0}</strong></div>
            <div><span>未变化</span><strong>{summary?.unchanged ?? 0}</strong></div>
            <div><span>比较文档</span><strong>{summary?.compared_paths ?? 0}</strong></div>
          </div>

          <section className="version-diff-section">
            <Typography.Title level={5}>元数据差异</Typography.Title>
            {diffQuery.data.metadata_changes.length ? (
              <Table
                rowKey="field"
                size="small"
                pagination={false}
                columns={metadataColumns}
                dataSource={diffQuery.data.metadata_changes}
                scroll={{ x: 720 }}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="元数据没有变化" />
            )}
          </section>

          <section className="version-diff-section">
            <div className="version-diff-section-heading">
              <Typography.Title level={5}>文件差异</Typography.Title>
              <Typography.Text type="secondary">
                共 {diffQuery.data.asset_summary?.returned ?? 0} 项变化
              </Typography.Text>
            </div>
            {(diffQuery.data.asset_changes || []).length ? (
              <Table
                rowKey="path"
                size="small"
                columns={assetColumns}
                dataSource={diffQuery.data.asset_changes}
                pagination={diffQuery.data.asset_changes.length > 20 ? { pageSize: 20 } : false}
                scroll={{ x: 900 }}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="文件没有变化" />
            )}
          </section>

          <section className="version-diff-section">
            <div className="version-diff-section-heading">
              <Typography.Title level={5}>内容差异</Typography.Title>
              <Segmented
                value={filter}
                onChange={(value) => setFilter(value as ChangeFilter)}
                options={[
                  { label: `全部 ${summary?.returned ?? 0}`, value: 'all' },
                  { label: `修改 ${summary?.modified ?? 0}`, value: 'modified' },
                  { label: `新增 ${summary?.added ?? 0}`, value: 'added' },
                  { label: `删除 ${summary?.removed ?? 0}`, value: 'removed' },
                ]}
              />
            </div>
            {documentItems.length ? (
              <Collapse className="version-diff-documents" items={documentItems} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选没有内容差异" />
            )}
          </section>
        </div>
      ) : null}
    </Modal>
  );
}
