import { ReloadOutlined, SaveOutlined, SyncOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Descriptions, Form, InputNumber, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { getApiErrorMessage } from '../api/client';
import { rebuildSearchIndex } from '../api/search';
import { getHealth, getSettings, updateSettings, type SystemSettings } from '../api/settings';
import { ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { StatusTag } from '../components/package/StatusTag';
import { useSession } from '../hooks/useSession';

const fields = [
  { key: 'upload.max_package_size_mb', label: '单个上传包上限', unit: 'MB', min: 1, max: 5120 },
  { key: 'upload.max_unpacked_size_mb', label: '解压后总大小上限', unit: 'MB', min: 1, max: 51200 },
  { key: 'upload.max_file_count', label: '解压文件数量上限', unit: '个', min: 1, max: 100000 },
  { key: 'upload.max_single_file_size_mb', label: '单文件大小上限', unit: 'MB', min: 1, max: 5120 },
  { key: 'upload.max_path_length', label: '路径长度上限', unit: '字符', min: 64, max: 512 },
  { key: 'cleanup.soft_delete_retention_days', label: '软删除保留时间', unit: '天', min: 1, max: 3650 },
  { key: 'task.poll_interval_seconds', label: '任务轮询间隔', unit: '秒', min: 1, max: 86400 },
  { key: 'task.thread_pool_size', label: '解析线程数', unit: '个', min: 1, max: 64 },
  { key: 'preview.ticket_ttl_seconds', label: '预览票据有效期', unit: '秒', min: 10, max: 3600 },
  { key: 'preview.session_ttl_seconds', label: '预览会话有效期', unit: '秒', min: 60, max: 86400 },
] as const;

export function SettingsPage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const session = useSession();
  const [form] = Form.useForm<SystemSettings>();
  const settings = useQuery({ queryKey: ['settings'], queryFn: getSettings, retry: false });
  const health = useQuery({ queryKey: ['health'], queryFn: () => getHealth() });
  const dbHealth = useQuery({ queryKey: ['health', 'db'], queryFn: () => getHealth('db'), retry: false });
  const searchHealth = useQuery({ queryKey: ['health', 'search'], queryFn: () => getHealth('search'), retry: false });
  const storageHealth = useQuery({ queryKey: ['health', 'storage'], queryFn: () => getHealth('storage'), retry: false });
  const canEdit = ['owner', 'admin'].includes(session.data?.role?.toLowerCase() || '');

  useEffect(() => {
    if (settings.data) form.setFieldsValue(settings.data);
  }, [form, settings.data]);

  const saveMutation = useMutation({
    mutationFn: (values: SystemSettings) => updateSettings(values),
    onSuccess: (data) => {
      message.success('系统设置已保存');
      queryClient.setQueryData(['settings'], data);
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });
  const reindexMutation = useMutation({
    mutationFn: rebuildSearchIndex,
    onSuccess: () => message.success('搜索索引重建任务已启动'),
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const healthItems = [
    ['主服务', health],
    ['数据库', dbHealth],
    ['搜索', searchHealth],
    ['对象存储', storageHealth],
  ] as const;

  return (
    <div className="settings-page management-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">System control</span>
          <Typography.Title level={1}>系统设置</Typography.Title>
          <Typography.Paragraph type="secondary">管理上传限制、后台任务和预览会话参数。</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<SaveOutlined />} disabled={!canEdit} loading={saveMutation.isPending} onClick={() => form.submit()}>保存设置</Button>
      </div>

      <section className="settings-section">
        <div className="section-heading">
          <h2>服务状态</h2>
          <Button type="text" icon={<ReloadOutlined />} onClick={() => void queryClient.invalidateQueries({ queryKey: ['health'] })}>刷新</Button>
        </div>
        <div className="health-grid">
          {healthItems.map(([label, query]) => (
            <div className="health-item" key={label}>
              <span>{label}</span>
              {query.isPending ? <Typography.Text type="secondary">检查中</Typography.Text> : query.isError ? <StatusTag status="failed" /> : <StatusTag status={query.data?.status === 'UP' ? 'success' : query.data?.status} />}
              {query.data?.detail ? <Typography.Text type="secondary">{query.data.detail}</Typography.Text> : null}
            </div>
          ))}
        </div>
      </section>

      <section className="settings-section">
        <div className="section-heading"><h2>运行参数</h2></div>
        {!canEdit ? <Typography.Paragraph type="secondary">当前账号仅可查看设置，owner 或 admin 可以修改。</Typography.Paragraph> : null}
        {settings.isPending ? <LoadingBlock rows={8} /> : null}
        {settings.isError ? <ErrorBlock title="无法读取系统设置" description={getApiErrorMessage(settings.error)} onRetry={() => void settings.refetch()} /> : null}
        {settings.data ? (
          <Form form={form} layout="vertical" disabled={!canEdit} onFinish={(values) => saveMutation.mutate(values)}>
            <div className="settings-grid">
              {fields.map((field) => (
                <Form.Item key={field.key} name={field.key} label={field.label} rules={[{ required: true, message: '请输入有效数值' }]}>
                  <InputNumber min={field.min} max={field.max} suffix={field.unit} precision={0} style={{ width: '100%' }} />
                </Form.Item>
              ))}
            </div>
          </Form>
        ) : null}
      </section>

      <section className="settings-section">
        <div className="section-heading"><h2>维护操作</h2></div>
        <Descriptions column={1} colon={false} className="maintenance-list">
          <Descriptions.Item label="重建搜索索引">
            <Space>
              <Typography.Text type="secondary">重新把当前知识包章节写入搜索服务。</Typography.Text>
              <Button
                icon={<SyncOutlined />}
                disabled={!canEdit}
                loading={reindexMutation.isPending}
                onClick={() => modal.confirm({
                  title: '重建全部搜索索引？',
                  content: '重建期间搜索结果可能短暂不完整，知识包浏览不受影响。',
                  okText: '开始重建',
                  cancelText: '取消',
                  onOk: () => reindexMutation.mutateAsync(),
                })}
              >
                重建索引
              </Button>
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </section>
    </div>
  );
}
