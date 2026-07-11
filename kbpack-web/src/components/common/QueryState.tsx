import { Alert, Button, Empty, Skeleton, Space } from 'antd';

export function LoadingBlock({ rows = 4 }: { rows?: number }) {
  return (
    <div className="state-block" aria-label="正在加载">
      <Skeleton active paragraph={{ rows }} title />
    </div>
  );
}

export function ErrorBlock({
  title = '暂时无法加载',
  description = '请检查服务状态后重试。',
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <Alert
      className="state-alert"
      type="warning"
      showIcon
      message={title}
      description={
        <Space direction="vertical" size={12}>
          <span>{description}</span>
          {onRetry ? (
            <Button size="small" onClick={onRetry}>
              重新加载
            </Button>
          ) : null}
        </Space>
      }
    />
  );
}

export function EmptyBlock({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <Empty description={description ? `${title} · ${description}` : title} image={Empty.PRESENTED_IMAGE_SIMPLE}>
      {action}
    </Empty>
  );
}
