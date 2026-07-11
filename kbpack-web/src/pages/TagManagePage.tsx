import { DeleteOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Form, Input, Modal, Table, Typography, type TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
import { createTag, deleteTag, listTags, type TagItem } from '../api/tags';
import { EmptyBlock, ErrorBlock } from '../components/common/QueryState';
import { formatDate } from '../utils/format';

export function TagManagePage() {
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [createOpen, setCreateOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [form] = Form.useForm<{ name: string }>();
  const tags = useQuery({ queryKey: ['tags'], queryFn: listTags });
  const filtered = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return value ? (tags.data || []).filter((tag) => tag.name.toLowerCase().includes(value)) : tags.data || [];
  }, [keyword, tags.data]);

  const createMutation = useMutation({
    mutationFn: ({ name }: { name: string }) => createTag(name),
    onSuccess: () => {
      message.success('标签已创建');
      setCreateOpen(false);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['tags'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });
  const deleteMutation = useMutation({
    mutationFn: deleteTag,
    onSuccess: () => {
      message.success('标签已删除');
      void queryClient.invalidateQueries({ queryKey: ['tags'] });
      void queryClient.invalidateQueries({ queryKey: ['packages'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const confirmDelete = (tag: TagItem) => {
    modal.confirm({
      title: `删除标签“${tag.name}”？`,
      content: tag.package_count ? `该标签关联 ${tag.package_count} 个知识包，删除后关联会一并解除。` : '删除后无法恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(tag.id),
    });
  };

  const columns: TableColumnsType<TagItem> = [
    {
      title: '标签',
      dataIndex: 'name',
      render: (name) => <Link className="management-name" to={`/packages?tag=${encodeURIComponent(name)}`}>{name}</Link>,
    },
    { title: '知识包', dataIndex: 'package_count', width: 120, render: (value) => value ?? '—' },
    { title: '创建时间', dataIndex: 'created_at', width: 180, render: (value) => formatDate(value) },
    {
      title: '操作',
      width: 90,
      align: 'right',
      render: (_, tag) => <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${tag.name}`} onClick={() => confirmDelete(tag)} />,
    },
  ];

  return (
    <div className="management-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">Taxonomy</span>
          <Typography.Title level={1}>标签管理</Typography.Title>
          <Typography.Paragraph type="secondary">维护跨知识包使用的主题标签。</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>新建标签</Button>
      </div>
      <div className="toolbar-row">
        <Input prefix={<SearchOutlined />} allowClear placeholder="查找标签" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <Typography.Text type="secondary">{filtered.length} 个标签</Typography.Text>
      </div>
      {tags.isError ? <ErrorBlock description={getApiErrorMessage(tags.error)} onRetry={() => void tags.refetch()} /> : null}
      {!tags.isPending && !tags.isError && filtered.length === 0 ? <EmptyBlock title={keyword ? '没有匹配的标签' : '还没有标签'} /> : null}
      <Table rowKey="id" columns={columns} dataSource={filtered} loading={tags.isPending} pagination={{ pageSize: 20, hideOnSinglePage: true }} scroll={{ x: 620 }} />

      <Modal title="新建标签" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} confirmLoading={createMutation.isPending} okText="创建" cancelText="取消">
        <Form form={form} layout="vertical" onFinish={(values) => createMutation.mutate({ name: values.name.trim() })}>
          <Form.Item name="name" label="标签名称" rules={[{ required: true, whitespace: true, message: '请输入标签名称' }, { max: 64, message: '最多 64 个字符' }]}>
            <Input autoFocus placeholder="例如：架构、排障" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
