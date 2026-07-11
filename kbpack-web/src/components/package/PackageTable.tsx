import { DownloadOutlined, EyeOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { Button, Space, Table, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { PackageListItem } from '../../api/packages';
import { packageDownloadUrl } from '../../api/packages';
import { formatBytes, formatRelativeDate } from '../../utils/format';
import { StatusTag } from './StatusTag';

export function PackageTable({
  items,
  loading,
  onFavorite,
}: {
  items: PackageListItem[];
  loading?: boolean;
  onFavorite: (item: PackageListItem) => void;
}) {
  const navigate = useNavigate();
  const columns: TableColumnsType<PackageListItem> = [
    {
      title: '标题',
      dataIndex: 'title',
      width: 330,
      render: (_, item) => (
        <div className="table-title-cell">
          <Typography.Text strong>{item.title}</Typography.Text>
          <Typography.Text type="secondary" ellipsis>
            {(item.tags || []).join(' · ') || '暂无标签'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '状态',
      width: 110,
      render: (_, item) => <StatusTag status={item.current_version?.parse_status || item.status} />,
    },
    {
      title: '版本',
      width: 80,
      render: (_, item) => (item.current_version ? `v${item.current_version.version_no}` : '—'),
    },
    { title: '文件', dataIndex: 'file_count', width: 80, render: (value) => value ?? '—' },
    {
      title: '大小',
      dataIndex: 'unpacked_size',
      width: 100,
      render: (value) => formatBytes(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updated_at',
      width: 110,
      render: (value) => formatRelativeDate(value),
    },
    {
      title: '操作',
      width: 142,
      align: 'right',
      render: (_, item) => (
        <Space size={2} onClick={(event) => event.stopPropagation()}>
          <Tooltip title={item.is_favorite ? '取消收藏' : '收藏'}>
            <Button
              type="text"
              icon={item.is_favorite ? <StarFilled /> : <StarOutlined />}
              aria-label={item.is_favorite ? '取消收藏' : '收藏'}
              onClick={() => onFavorite(item)}
            />
          </Tooltip>
          {item.current_version ? (
            <>
              <Tooltip title="预览">
                <Button
                  type="text"
                  icon={<EyeOutlined />}
                  aria-label="预览"
                  onClick={() =>
                    navigate(`/packages/${item.id}/preview/${item.current_version?.id}`)
                  }
                />
              </Tooltip>
              <Tooltip title="下载">
                <Button
                  type="text"
                  icon={<DownloadOutlined />}
                  aria-label="下载"
                  href={packageDownloadUrl(item.current_version.id)}
                />
              </Tooltip>
            </>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Table
      className="package-table"
      rowKey="id"
      columns={columns}
      dataSource={items}
      loading={loading}
      pagination={false}
      scroll={{ x: 940 }}
      onRow={(item) => ({ onClick: () => navigate(`/packages/${item.id}`) })}
    />
  );
}
