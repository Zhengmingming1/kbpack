import { DeleteOutlined, UndoOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Pagination, Space, Table, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/client';
import {
  listTrashPackages,
  permanentlyDeleteTrashPackage,
  restoreTrashPackage,
  type TrashPackageItem,
} from '../api/trash';
import { EmptyBlock, ErrorBlock } from '../components/common/QueryState';
import { formatDate } from '../utils/format';

type TrashAction = { action: 'restore' | 'delete'; item: TrashPackageItem };

export function TrashPage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const page = Math.max(Number(params.get('page')) || 1, 1);
  const trash = useQuery({
    queryKey: ['trash', 'packages', page],
    queryFn: () => listTrashPackages({ page, page_size: 20 }),
  });
  const actionMutation = useMutation({
    mutationFn: async ({ action, item }: TrashAction) => {
      if (action === 'restore') await restoreTrashPackage(item.id);
      else await permanentlyDeleteTrashPackage(item.id);
    },
    onSuccess: (_, variables) => {
      message.success(variables.action === 'restore' ? '知识包已恢复' : '知识包已永久删除');
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['trash'] }),
        queryClient.invalidateQueries({ queryKey: ['packages'] }),
        queryClient.invalidateQueries({ queryKey: ['stats'] }),
      ]);
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const confirmDelete = (item: TrashPackageItem) => {
    modal.confirm({
      title: `永久删除“${item.title}”？`,
      content: '原始文件、抽取内容、版本和索引记录都会被永久移除，此操作无法恢复。',
      okText: '永久删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => actionMutation.mutateAsync({ action: 'delete', item }),
    });
  };

  const columns: TableColumnsType<TrashPackageItem> = [
    {
      title: '知识包',
      dataIndex: 'title',
      render: (title, item) => (
        <div className="table-title-cell">
          <Typography.Text strong>{title}</Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {(item.tags || []).join(' · ') || item.description || '暂无描述'}
          </Typography.Text>
        </div>
      ),
    },
    { title: '删除时间', dataIndex: 'deleted_at', width: 170, render: formatDate },
    { title: '预计清理', dataIndex: 'purge_at', width: 170, render: formatDate },
    {
      title: '操作',
      width: 118,
      align: 'right',
      render: (_, item) => (
        <Space size={2}>
          {item.can_restore !== false ? (
            <Tooltip title="恢复知识包">
              <Button
                type="text"
                icon={<UndoOutlined />}
                aria-label={`恢复${item.title}`}
                loading={actionMutation.isPending
                  && actionMutation.variables?.action === 'restore'
                  && actionMutation.variables.item.id === item.id}
                onClick={() => actionMutation.mutate({ action: 'restore', item })}
              />
            </Tooltip>
          ) : null}
          {item.can_delete !== false ? (
            <Tooltip title="永久删除">
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label={`永久删除${item.title}`}
                onClick={() => confirmDelete(item)}
              />
            </Tooltip>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div className="management-page trash-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">回收管理</span>
          <Typography.Title level={1}>回收站</Typography.Title>
          <Typography.Paragraph type="secondary">恢复误删内容，或永久清理不再需要的知识包。</Typography.Paragraph>
        </div>
      </div>

      {trash.isError ? (
        <ErrorBlock description={getApiErrorMessage(trash.error)} onRetry={() => void trash.refetch()} />
      ) : null}
      {!trash.isPending && !trash.isError && trash.data?.items.length === 0 ? (
        <EmptyBlock title="回收站为空" description="删除的知识包会暂时保留在这里。" />
      ) : null}
      {trash.isPending || trash.data?.items.length ? (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={trash.data?.items || []}
          loading={trash.isPending}
          pagination={false}
          scroll={{ x: 760 }}
        />
      ) : null}
      {trash.data && trash.data.total > trash.data.page_size ? (
        <Pagination
          className="list-pagination"
          current={trash.data.page}
          pageSize={trash.data.page_size}
          total={trash.data.total}
          showSizeChanger={false}
          onChange={(next) => setParams(next > 1 ? { page: String(next) } : {})}
        />
      ) : null}
    </div>
  );
}
