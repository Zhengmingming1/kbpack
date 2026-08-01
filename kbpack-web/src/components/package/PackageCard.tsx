import { DownloadOutlined, EyeOutlined, FileTextOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { Button, Space, Tooltip, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { PackageListItem } from '../../api/packages';
import { packageDownloadUrl } from '../../api/packages';
import { packageSourceLabel } from '../../constants/packageOptions';
import { formatBytes, formatRelativeDate } from '../../utils/format';
import { resolvePackageAssetUrl } from '../../utils/packageAssetUrl';
import { StatusTag } from './StatusTag';

export function PackageCard({
  item,
  onFavorite,
  compact = false,
}: {
  item: PackageListItem;
  onFavorite?: (item: PackageListItem) => void;
  compact?: boolean;
}) {
  const navigate = useNavigate();
  const version = item.current_version;
  const [coverFailed, setCoverFailed] = useState(false);
  const coverUrl = resolvePackageAssetUrl(item.cover_url || undefined, undefined, version?.id);

  useEffect(() => setCoverFailed(false), [coverUrl]);

  return (
    <article className={`package-card${compact ? ' package-card-compact' : ''}`}>
      <Link className="package-card-main" to={`/packages/${item.id}`} aria-label={`打开知识包：${item.title}`}>
        <div className="package-card-cover">
          {coverUrl && !coverFailed ? (
            <img src={coverUrl} alt="" loading="lazy" onError={() => setCoverFailed(true)} />
          ) : (
            <div className="package-card-cover-fallback" aria-hidden="true">
              <FileTextOutlined />
              <span>{packageSourceLabel(item.source_type) || '知识资料'}</span>
            </div>
          )}
        </div>
        <div className="package-card-body">
          <div className="package-card-kicker">
            <span>{version ? `v${version.version_no}` : '尚无版本'}</span>
            <span>{formatRelativeDate(item.updated_at)}</span>
          </div>
          <Typography.Title level={3}>{item.title}</Typography.Title>
          {compact ? null : (
            <Typography.Paragraph className="package-description" ellipsis={{ rows: 2 }}>
              {item.description || '暂无描述'}
            </Typography.Paragraph>
          )}
          <div className="package-card-tags">
            {(item.tags || []).slice(0, 4).map((tag) => (
              <span key={tag} className="text-tag">
                {tag}
              </span>
            ))}
          </div>
          <div className="package-card-meta">
            <StatusTag status={version?.parse_status || item.status} />
            <span>{item.file_count ?? '—'} 文件</span>
            <span>{formatBytes(item.unpacked_size)}</span>
          </div>
        </div>
      </Link>
      <div className="package-card-actions">
        <Space size={4}>
          {onFavorite ? (
            <Tooltip title={item.is_favorite ? '取消收藏' : '收藏'}>
              <Button
                type="text"
                icon={item.is_favorite ? <StarFilled /> : <StarOutlined />}
                aria-label={item.is_favorite ? '取消收藏' : '收藏'}
                onClick={() => onFavorite(item)}
              />
            </Tooltip>
          ) : null}
          {version ? (
            <>
              <Tooltip title="原样预览">
                <Button
                  type="text"
                  icon={<EyeOutlined />}
                  aria-label="原样预览"
                  onClick={() => navigate(`/packages/${item.id}/preview/${version.id}`)}
                />
              </Tooltip>
              <Tooltip title="下载原始包">
                <Button
                  type="text"
                  icon={<DownloadOutlined />}
                  aria-label="下载原始包"
                  href={packageDownloadUrl(version.id)}
                />
              </Tooltip>
            </>
          ) : null}
        </Space>
      </div>
    </article>
  );
}
