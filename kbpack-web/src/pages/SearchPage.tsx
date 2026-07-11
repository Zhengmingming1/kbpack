import { EyeOutlined, FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Input, Pagination, Select, Space, Typography } from 'antd';
import { Fragment, type ReactNode, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { listCollections, type CollectionItem } from '../api/collections';
import { getApiErrorMessage } from '../api/client';
import { searchKnowledge } from '../api/search';
import { listTags } from '../api/tags';
import { EmptyBlock, ErrorBlock, LoadingBlock } from '../components/common/QueryState';
import { formatRelativeDate } from '../utils/format';

function flattenCollections(items: CollectionItem[]): CollectionItem[] {
  return items.flatMap((item) => [item, ...flattenCollections(item.children || [])]);
}

function highlightedSnippet(value: string): ReactNode {
  const parsed = new DOMParser().parseFromString(`<div>${value}</div>`, 'text/html');
  const root = parsed.body.firstElementChild;
  if (!root) return value;
  return Array.from(root.childNodes).map((node, index) =>
    node.nodeName.toLowerCase() === 'em' ? (
      <mark key={index}>{node.textContent}</mark>
    ) : (
      <Fragment key={index}>{node.textContent}</Fragment>
    ),
  );
}

export function SearchPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [input, setInput] = useState(params.get('q') || '');
  const [filterOpen, setFilterOpen] = useState(false);
  const q = params.get('q')?.trim() || '';
  const page = Math.max(Number(params.get('page')) || 1, 1);

  useEffect(() => setInput(q), [q]);

  const filters = useMemo(
    () => ({
      q,
      tag: params.get('tag') || undefined,
      collection: params.get('collection') || undefined,
      source: params.get('source') || undefined,
      status: params.get('status') || undefined,
      package_id: params.get('package_id') || undefined,
      page,
      page_size: 20,
    }),
    [page, params, q],
  );
  const results = useQuery({
    queryKey: ['search', filters],
    queryFn: () => searchKnowledge(filters),
    enabled: Boolean(q),
  });
  const tags = useQuery({ queryKey: ['tags'], queryFn: listTags });
  const collections = useQuery({ queryKey: ['collections'], queryFn: listCollections });

  const updateParam = (key: string, value?: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  };

  const filterForm = (
    <div className="filter-form">
      <label>
        <span>标签</span>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          value={params.get('tag') || undefined}
          placeholder="全部标签"
          onChange={(value) => updateParam('tag', value)}
          options={(tags.data || []).map((tag) => ({ value: tag.name, label: tag.name }))}
        />
      </label>
      <label>
        <span>集合</span>
        <Select
          allowClear
          value={params.get('collection') || undefined}
          placeholder="全部集合"
          onChange={(value) => updateParam('collection', value)}
          options={flattenCollections(collections.data || []).map((item) => ({ value: item.id, label: item.name }))}
        />
      </label>
      <label>
        <span>状态</span>
        <Select
          allowClear
          value={params.get('status') || undefined}
          placeholder="全部状态"
          onChange={(value) => updateParam('status', value)}
          options={[{ value: 'active', label: '可用' }, { value: 'archived', label: '已归档' }, { value: 'failed', label: '解析失败' }]}
        />
      </label>
      <label>
        <span>来源</span>
        <Select
          allowClear
          value={params.get('source') || undefined}
          placeholder="全部来源"
          onChange={(value) => updateParam('source', value)}
          options={[{ value: 'manual', label: '手工上传' }, { value: 'ai_generated', label: 'AI 生成' }, { value: 'imported', label: '外部导入' }]}
        />
      </label>
      <Button onClick={() => {
        const next = new URLSearchParams();
        if (q) next.set('q', q);
        if (params.get('package_id')) next.set('package_id', params.get('package_id')!);
        setParams(next);
      }}>清空筛选</Button>
    </div>
  );

  return (
    <div className="search-page">
      <div className="page-heading search-heading">
        <div>
          <span className="eyebrow">Search archive</span>
          <Typography.Title level={1}>搜索</Typography.Title>
          <Typography.Paragraph type="secondary">在知识包标题、章节正文和标签中查找内容。</Typography.Paragraph>
        </div>
      </div>

      <div className="search-input-row">
        <Input.Search
          size="large"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onSearch={(value) => updateParam('q', value.trim())}
          enterButton={<><SearchOutlined /> 搜索</>}
          placeholder="输入关键词"
          allowClear
        />
        <Button className="mobile-filter-trigger" size="large" icon={<FilterOutlined />} onClick={() => setFilterOpen(true)}>筛选</Button>
      </div>

      {params.get('package_id') ? (
        <div className="active-scope">
          <span>当前范围：指定知识包</span>
          <Button type="link" size="small" onClick={() => updateParam('package_id')}>搜索全部</Button>
        </div>
      ) : null}

      <div className="search-layout">
        <aside className="desktop-filter-rail search-filter-rail">
          <div className="section-heading"><h3>筛选结果</h3></div>
          {filterForm}
        </aside>
        <div className="search-results">
          {!q ? (
            <section className="search-empty-start">
              <EmptyBlock title="输入关键词开始搜索" description="可按标签、集合、来源和状态进一步缩小范围。" />
              {tags.data?.length ? (
                <div className="suggested-tags">
                  <Typography.Text type="secondary">常用标签</Typography.Text>
                  <Space wrap>
                    {tags.data.slice(0, 10).map((tag) => (
                      <Button key={tag.id} size="small" onClick={() => {
                        const next = new URLSearchParams(params);
                        next.set('q', tag.name);
                        next.set('tag', tag.name);
                        setParams(next);
                      }}>{tag.name}</Button>
                    ))}
                  </Space>
                </div>
              ) : null}
            </section>
          ) : null}
          {results.isPending && q ? <LoadingBlock rows={8} /> : null}
          {results.isError ? (
            <ErrorBlock
              title="搜索暂不可用"
              description={getApiErrorMessage(results.error)}
              onRetry={() => void results.refetch()}
            />
          ) : null}
          {results.data ? (
            <div className="search-result-summary">
              找到 <strong>{results.data.total}</strong> 条与“{q}”相关的内容
            </div>
          ) : null}
          {results.data?.items.length === 0 ? (
            <EmptyBlock title="没有找到相关内容" description="可以换个关键词，或检查知识包是否已完成解析。" />
          ) : null}
          <div className="search-result-list">
            {results.data?.items.map((item) => (
              <article className="search-result-item" key={`${item.document_id}-${item.anchor || ''}`}>
                <div className="search-result-kicker">
                  <Link to={`/packages/${item.package_id}`}>{item.package_title}</Link>
                  <time>{formatRelativeDate(item.updated_at)}</time>
                </div>
                <Typography.Title level={2}>
                  <Link to={`/documents/${item.document_id}`}>{item.document_title}</Link>
                </Typography.Title>
                <p className="search-snippet">{highlightedSnippet(item.snippet)}</p>
                <div className="search-result-footer">
                  <Space wrap>
                    {(item.tags || []).map((tag) => <span className="text-tag" key={tag}>{tag}</span>)}
                  </Space>
                  <Space>
                    <Button type="link" size="small" onClick={() => navigate(`/packages/${item.package_id}`)}>详情</Button>
                    <Button type="primary" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/documents/${item.document_id}`)}>打开章节</Button>
                  </Space>
                </div>
              </article>
            ))}
          </div>
          {results.data && results.data.total > results.data.page_size ? (
            <Pagination
              current={page}
              pageSize={results.data.page_size}
              total={results.data.total}
              showSizeChanger={false}
              onChange={(next) => updateParam('page', String(next))}
            />
          ) : null}
        </div>
      </div>

      <Drawer title="筛选搜索结果" placement="bottom" height="auto" open={filterOpen} onClose={() => setFilterOpen(false)}>
        {filterForm}
      </Drawer>
    </div>
  );
}
