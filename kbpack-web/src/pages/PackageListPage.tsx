import {
  AppstoreOutlined,
  CloudUploadOutlined,
  FilterOutlined,
  ReloadOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Pagination,
  Segmented,
  Select,
  Space,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { listCollections, type CollectionItem } from '../api/collections';
import { getApiErrorMessage } from '../api/client';
import { listPackages, setFavorite, type PackageListItem } from '../api/packages';
import { listTags } from '../api/tags';
import { EmptyBlock, ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { PackageCard } from '../components/package/PackageCard';
import { PackageTable } from '../components/package/PackageTable';
import { PACKAGE_SOURCE_OPTIONS, PACKAGE_STATUS_OPTIONS } from '../constants/packageOptions';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { useUiStore } from '../stores/uiStore';

function flattenCollections(items: CollectionItem[], level = 0): Array<CollectionItem & { level: number }> {
  return items.flatMap((item) => [
    { ...item, level },
    ...flattenCollections(item.children || [], level + 1),
  ]);
}

export function PackageListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [params, setParams] = useSearchParams();
  const [keyword, setKeyword] = useState(params.get('keyword') || '');
  const isMobile = useMediaQuery('(max-width: 639px)');
  const { filterPanelOpen, packageViewMode, setFilterPanelOpen, setPackageViewMode } = useUiStore();

  const page = Math.max(Number(params.get('page')) || 1, 1);
  const filters = useMemo(
    () => ({
      keyword: params.get('keyword') || undefined,
      tag: params.get('tag') || undefined,
      collection: params.get('collection') || undefined,
      status: params.get('status') || undefined,
      source: params.get('source') || undefined,
      favorite: params.get('favorite') === 'true' ? true : undefined,
      page,
      page_size: 20,
    }),
    [page, params],
  );

  const packages = useQuery({
    queryKey: ['packages', filters],
    queryFn: () => listPackages(filters),
  });
  const tags = useQuery({ queryKey: ['tags'], queryFn: listTags });
  const collections = useQuery({ queryKey: ['collections'], queryFn: listCollections });
  const collectionOptions = flattenCollections(collections.data || []);

  const favoriteMutation = useMutation({
    mutationFn: (item: PackageListItem) => setFavorite(item.id, !item.is_favorite),
    onSuccess: (_, item) => {
      message.success(item.is_favorite ? '已取消收藏' : '已收藏');
      void queryClient.invalidateQueries({ queryKey: ['packages'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error)),
  });

  const updateParam = (key: string, value?: string | boolean) => {
    const next = new URLSearchParams(params);
    if (value === undefined || value === '' || value === false) next.delete(key);
    else next.set(key, String(value));
    if (key !== 'page') next.delete('page');
    setParams(next);
  };

  const resetFilters = () => {
    setKeyword('');
    setParams({});
  };

  const filterContent = (
    <div className="filter-form">
      <label>
        <span>集合</span>
        <Select
          allowClear
          placeholder="全部集合"
          value={params.get('collection') || undefined}
          onChange={(value) => updateParam('collection', value)}
          options={collectionOptions.map((item) => ({
            value: item.id,
            label: `${'　'.repeat(item.level)}${item.name}`,
          }))}
        />
      </label>
      <label>
        <span>标签</span>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="全部标签"
          value={params.get('tag') || undefined}
          onChange={(value) => updateParam('tag', value)}
          options={(tags.data || []).map((tag) => ({ value: tag.name, label: tag.name }))}
        />
      </label>
      <label>
        <span>状态</span>
        <Select
          allowClear
          placeholder="全部状态"
          value={params.get('status') || undefined}
          onChange={(value) => updateParam('status', value)}
          options={PACKAGE_STATUS_OPTIONS}
        />
      </label>
      <label>
        <span>来源</span>
        <Select
          allowClear
          placeholder="全部来源"
          value={params.get('source') || undefined}
          onChange={(value) => updateParam('source', value)}
          options={PACKAGE_SOURCE_OPTIONS}
        />
      </label>
      <Checkbox
        checked={params.get('favorite') === 'true'}
        onChange={(event) => updateParam('favorite', event.target.checked)}
      >
        只看收藏
      </Checkbox>
      <Button icon={<ReloadOutlined />} onClick={resetFilters}>
        重置筛选
      </Button>
    </div>
  );

  const effectiveMode = isMobile ? 'card' : packageViewMode;

  return (
    <div>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Knowledge packages</span>
          <Typography.Title level={1}>知识包</Typography.Title>
          <Typography.Paragraph type="secondary">
            {packages.data ? `共 ${packages.data.total} 个知识包` : '浏览、筛选并管理归档内容'}
          </Typography.Paragraph>
        </div>
        <Button className="redundant-mobile-action" type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/packages/upload')}>
          上传
        </Button>
      </div>

      <div className="package-list-layout">
        <aside className="desktop-filter-rail surface surface-pad">
          <div className="section-heading">
            <h3>筛选</h3>
          </div>
          {filterContent}
        </aside>

        <div className="package-list-main">
          <div className="toolbar-row">
            <Input.Search
              allowClear
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onSearch={(value) => updateParam('keyword', value.trim())}
              placeholder="搜索标题或描述"
            />
            <Space>
              <Button
                className="mobile-filter-trigger"
                icon={<FilterOutlined />}
                onClick={() => setFilterPanelOpen(true)}
              >
                筛选
              </Button>
              {isMobile ? null : (
                <Segmented
                  value={packageViewMode}
                  onChange={(value) => setPackageViewMode(value as 'card' | 'table')}
                  options={[
                    { value: 'card', icon: <AppstoreOutlined />, label: '卡片' },
                    { value: 'table', icon: <TableOutlined />, label: '表格' },
                  ]}
                />
              )}
            </Space>
          </div>

          {packages.isPending ? <LoadingBlock rows={7} /> : null}
          {packages.isError ? (
            <ErrorBlock
              description={getApiErrorMessage(packages.error)}
              onRetry={() => void packages.refetch()}
            />
          ) : null}
          {packages.data?.items.length === 0 ? (
            <EmptyBlock
              title="没有找到知识包"
              description="可以调整筛选条件，或上传新的知识包。"
              action={<Button onClick={resetFilters}>清空筛选</Button>}
            />
          ) : null}

          {packages.data?.items.length && effectiveMode === 'card' ? (
            <div className="package-grid">
              {packages.data.items.map((item) => (
                <PackageCard key={item.id} item={item} onFavorite={(value) => favoriteMutation.mutate(value)} />
              ))}
            </div>
          ) : null}
          {packages.data?.items.length && effectiveMode === 'table' ? (
            <PackageTable
              items={packages.data.items}
              onFavorite={(value) => favoriteMutation.mutate(value)}
            />
          ) : null}

          {packages.data && packages.data.total > packages.data.page_size ? (
            <Pagination
              className="list-pagination"
              current={page}
              pageSize={packages.data.page_size}
              total={packages.data.total}
              showSizeChanger={false}
              onChange={(nextPage) => updateParam('page', String(nextPage))}
            />
          ) : null}
        </div>
      </div>

      <Drawer
        title="筛选知识包"
        placement="bottom"
        height="auto"
        open={filterPanelOpen}
        onClose={() => setFilterPanelOpen(false)}
      >
        {filterContent}
      </Drawer>
    </div>
  );
}
