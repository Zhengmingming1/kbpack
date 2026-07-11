import {
  ArrowRightOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  InboxOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Col, Row, Space, Statistic, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { listPackages } from '../api/packages';
import { getStats } from '../api/stats';
import { listTasks, retryTask } from '../api/tasks';
import { getApiErrorMessage } from '../api/client';
import { EmptyBlock, ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { PackageCard } from '../components/package/PackageCard';
import { StatusTag } from '../components/package/StatusTag';
import { formatBytes, formatRelativeDate } from '../utils/format';
import { useSession } from '../hooks/useSession';

export function HomePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const session = useSession();
  const stats = useQuery({ queryKey: ['stats'], queryFn: getStats });
  const recent = useQuery({
    queryKey: ['packages', 'home', 'recent'],
    queryFn: () => listPackages({ page: 1, page_size: 6 }),
  });
  const favorites = useQuery({
    queryKey: ['packages', 'home', 'favorites'],
    queryFn: () => listPackages({ favorite: true, page: 1, page_size: 4 }),
  });
  const failedTasks = useQuery({
    queryKey: ['tasks', 'failed', 'home'],
    queryFn: () => listTasks({ status: 'failed', page: 1, page_size: 4 }),
    retry: false,
  });
  const retryMutation = useMutation({
    mutationFn: retryTask,
    onSuccess: () => {
      message.success('任务已重新排队');
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const displayName = session.data?.display_name || session.data?.username || '你';

  return (
    <div className="dashboard-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">Knowledge workspace</span>
          <Typography.Title level={1}>你好，{displayName}</Typography.Title>
          <Typography.Paragraph type="secondary">
            从最近更新的知识继续，或处理尚未完成的解析任务。
          </Typography.Paragraph>
        </div>
        <Button className="redundant-mobile-action" type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/packages/upload')}>
          上传知识包
        </Button>
      </div>

      <section className="dashboard-stats" aria-label="知识库统计">
        <Card>
          <Statistic
            title="知识包总数"
            value={stats.data?.package_count ?? 0}
            prefix={<InboxOutlined />}
            loading={stats.isPending}
          />
        </Card>
        <Card>
          <Statistic
            title="抽取章节"
            value={stats.data?.document_count ?? 0}
            prefix={<FileTextOutlined />}
            loading={stats.isPending}
          />
        </Card>
        <Card>
          <Statistic
            title="存储占用"
            value={formatBytes(stats.data?.storage_used_bytes)}
            prefix={<DatabaseOutlined />}
            loading={stats.isPending}
          />
        </Card>
        <Card className={(stats.data?.parse_failed_count || 0) > 0 ? 'stat-warning' : ''}>
          <Statistic
            title="解析失败"
            value={stats.data?.parse_failed_count ?? 0}
            prefix={<WarningOutlined />}
            loading={stats.isPending}
          />
        </Card>
      </section>

      {stats.isError ? (
        <ErrorBlock
          title="统计信息暂不可用"
          description={getApiErrorMessage(stats.error)}
          onRetry={() => void stats.refetch()}
        />
      ) : null}

      <div className="dashboard-layout">
        <div className="dashboard-main">
          <section className="dashboard-section">
            <div className="section-heading">
              <h2>最近更新</h2>
              <Link className="section-link" to="/packages">
                查看全部 <ArrowRightOutlined />
              </Link>
            </div>
            {recent.isPending ? <LoadingBlock rows={4} /> : null}
            {recent.isError ? (
              <ErrorBlock description={getApiErrorMessage(recent.error)} onRetry={() => void recent.refetch()} />
            ) : null}
            {recent.data && recent.data.items.length === 0 ? (
              <EmptyBlock
                title="还没有知识包"
                description="上传第一个 HTML 知识包，开始建立你的知识资产库。"
                action={
                  <Button type="primary" onClick={() => navigate('/packages/upload')}>
                    上传知识包
                  </Button>
                }
              />
            ) : null}
            {recent.data?.items.length ? (
              <div className="package-grid home-package-grid">
                {recent.data.items.map((item) => (
                  <PackageCard key={item.id} item={item} compact />
                ))}
              </div>
            ) : null}
          </section>

          <section className="dashboard-section">
            <div className="section-heading">
              <h2>收藏知识包</h2>
              <Link className="section-link" to="/packages?favorite=true">
                管理收藏 <ArrowRightOutlined />
              </Link>
            </div>
            {favorites.isPending ? <LoadingBlock rows={3} /> : null}
            {favorites.isError ? (
              <ErrorBlock description={getApiErrorMessage(favorites.error)} onRetry={() => void favorites.refetch()} />
            ) : null}
            {favorites.data && favorites.data.items.length === 0 ? (
              <EmptyBlock title="暂无收藏" description="在知识包列表中收藏常用资料。" />
            ) : null}
            {favorites.data?.items.length ? (
              <div className="package-grid home-package-grid">
                {favorites.data.items.map((item) => (
                  <PackageCard key={item.id} item={item} compact />
                ))}
              </div>
            ) : null}
          </section>
        </div>

        <aside className="dashboard-aside">
          <section className="aside-section">
            <div className="section-heading">
              <h3>待处理任务</h3>
            </div>
            {failedTasks.isPending ? <LoadingBlock rows={3} /> : null}
            {failedTasks.isError ? (
              <Typography.Paragraph type="secondary">
                任务服务暂不可用，其他知识浏览不受影响。
              </Typography.Paragraph>
            ) : null}
            {failedTasks.data && failedTasks.data.items.length === 0 ? (
              <EmptyBlock title="没有待处理任务" />
            ) : null}
            <div className="task-list">
              {failedTasks.data?.items.map((task) => (
                <div className="task-row" key={task.id}>
                  <div>
                    <StatusTag status={task.status} />
                    <Typography.Text strong>{task.package_title || '知识包解析任务'}</Typography.Text>
                    <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>
                      {task.error_message || '解析未完成'}
                    </Typography.Paragraph>
                  </div>
                  <Button
                    size="small"
                    loading={retryMutation.isPending && retryMutation.variables === task.id}
                    onClick={() => retryMutation.mutate(task.id)}
                  >
                    重试
                  </Button>
                </div>
              ))}
            </div>
          </section>

          <section className="aside-section">
            <div className="section-heading">
              <h3>最近上传</h3>
            </div>
            <Space direction="vertical" size={0} className="activity-list">
              {(stats.data?.recent_uploads || []).slice(0, 6).map((upload) => (
                <Link className="activity-row" key={`${upload.package_id}-${upload.created_at}`} to={`/packages/${upload.package_id}`}>
                  <span>{upload.title}</span>
                  <time>{formatRelativeDate(upload.created_at)}</time>
                </Link>
              ))}
            </Space>
            {stats.data && !stats.data.recent_uploads?.length ? (
              <Typography.Paragraph type="secondary">暂无最近上传记录。</Typography.Paragraph>
            ) : null}
          </section>

          <section className="quick-actions">
            <Row gutter={[10, 10]}>
              <Col span={12}>
                <Button block onClick={() => navigate('/packages/upload')}>上传</Button>
              </Col>
              <Col span={12}>
                <Button block onClick={() => navigate('/collections')}>新建集合</Button>
              </Col>
            </Row>
          </section>
        </aside>
      </div>
    </div>
  );
}
