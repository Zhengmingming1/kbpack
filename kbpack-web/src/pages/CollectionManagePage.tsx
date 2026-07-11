import { DeleteOutlined, EditOutlined, FolderOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tree, Typography, type TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import {
  createCollection,
  deleteCollection,
  listCollections,
  updateCollection,
  type CollectionItem,
} from '../api/collections';
import { getApiErrorMessage } from '../api/client';
import { EmptyBlock, ErrorBlock } from '../components/common/QueryState';

interface FlatCollection extends CollectionItem { depth: number }

function flatten(items: CollectionItem[], depth = 0): FlatCollection[] {
  return items.flatMap((item) => [{ ...item, depth }, ...flatten(item.children || [], depth + 1)]);
}

function treeData(items: CollectionItem[]): Array<{ key: string; title: string; children?: ReturnType<typeof treeData>; icon: React.ReactNode }> {
  return items.map((item) => ({ key: item.id, title: item.name, icon: <FolderOutlined />, children: treeData(item.children || []) }));
}

export function CollectionManagePage() {
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CollectionItem>();
  const [form] = Form.useForm<{ name: string; parent_id?: string; sort_order?: number }>();
  const collections = useQuery({ queryKey: ['collections'], queryFn: listCollections });
  const flat = useMemo(() => flatten(collections.data || []), [collections.data]);

  const saveMutation = useMutation({
    mutationFn: (values: { name: string; parent_id?: string; sort_order?: number }) =>
      editing ? updateCollection(editing.id, { ...values, parent_id: values.parent_id || null }) : createCollection(values),
    onSuccess: () => {
      message.success(editing ? '集合已更新' : '集合已创建');
      setModalOpen(false);
      setEditing(undefined);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['collections'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });
  const deleteMutation = useMutation({
    mutationFn: deleteCollection,
    onSuccess: () => {
      message.success('集合已删除');
      void queryClient.invalidateQueries({ queryKey: ['collections'] });
      void queryClient.invalidateQueries({ queryKey: ['packages'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const openCreate = (parentId?: string) => {
    setEditing(undefined);
    form.setFieldsValue({ name: '', parent_id: parentId, sort_order: 0 });
    setModalOpen(true);
  };
  const openEdit = (item: CollectionItem) => {
    setEditing(item);
    form.setFieldsValue({ name: item.name, parent_id: item.parent_id || undefined, sort_order: item.sort_order || 0 });
    setModalOpen(true);
  };
  const confirmDelete = (item: CollectionItem) => {
    modal.confirm({
      title: `删除集合“${item.name}”？`,
      content: item.children?.length ? '该集合仍有子集合，请先移动或删除子集合。' : '知识包不会被删除，但会解除与该集合的关联。',
      okText: '删除',
      okButtonProps: { danger: true, disabled: Boolean(item.children?.length) },
      cancelText: '取消',
      onOk: () => deleteMutation.mutateAsync(item.id),
    });
  };

  const columns: TableColumnsType<FlatCollection> = [
    {
      title: '集合名称',
      dataIndex: 'name',
      render: (name, item) => <div className="collection-name" style={{ paddingInlineStart: item.depth * 22 }}><FolderOutlined /><strong>{name}</strong></div>,
    },
    { title: '知识包', dataIndex: 'package_count', width: 100, render: (value) => value ?? '—' },
    { title: '排序', dataIndex: 'sort_order', width: 80, render: (value) => value ?? 0 },
    {
      title: '操作',
      width: 130,
      align: 'right',
      render: (_, item) => (
        <Space size={2}>
          <Button type="text" icon={<PlusOutlined />} aria-label={`在${item.name}下新建集合`} onClick={() => openCreate(item.id)} />
          <Button type="text" icon={<EditOutlined />} aria-label={`编辑${item.name}`} onClick={() => openEdit(item)} />
          <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${item.name}`} onClick={() => confirmDelete(item)} />
        </Space>
      ),
    },
  ];

  return (
    <div className="management-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">Collections</span>
          <Typography.Title level={1}>集合管理</Typography.Title>
          <Typography.Paragraph type="secondary">使用层级集合组织项目、主题和资料来源。</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新建集合</Button>
      </div>
      {collections.isError ? <ErrorBlock description={getApiErrorMessage(collections.error)} onRetry={() => void collections.refetch()} /> : null}
      {!collections.isPending && !collections.isError && flat.length === 0 ? <EmptyBlock title="还没有集合" action={<Button type="primary" onClick={() => openCreate()}>新建第一个集合</Button>} /> : null}
      <div className="collection-layout">
        <aside className="surface surface-pad collection-tree-panel">
          <div className="section-heading"><h3>集合结构</h3></div>
          <Tree showIcon defaultExpandAll treeData={treeData(collections.data || [])} />
        </aside>
        <div className="collection-table-panel">
          <Table rowKey="id" columns={columns} dataSource={flat} loading={collections.isPending} pagination={false} scroll={{ x: 620 }} />
        </div>
      </div>

      <Modal title={editing ? '编辑集合' : '新建集合'} open={modalOpen} onCancel={() => { setModalOpen(false); setEditing(undefined); }} onOk={() => form.submit()} confirmLoading={saveMutation.isPending} okText="保存" cancelText="取消">
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate({ ...values, name: values.name.trim() })}>
          <Form.Item name="name" label="集合名称" rules={[{ required: true, whitespace: true, message: '请输入集合名称' }, { max: 100, message: '最多 100 个字符' }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="parent_id" label="上级集合">
            <Select allowClear placeholder="无（顶级集合）" options={flat.filter((item) => item.id !== editing?.id).map((item) => ({ value: item.id, label: `${'　'.repeat(item.depth)}${item.name}` }))} />
          </Form.Item>
          <Form.Item name="sort_order" label="排序值">
            <InputNumber min={0} max={9999} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
