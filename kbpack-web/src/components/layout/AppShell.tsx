import { Avatar, Button, Dropdown, Input, Layout, Menu, Tooltip, Typography } from 'antd';
import type { InputRef } from 'antd';
import {
  AppstoreOutlined,
  CloudUploadOutlined,
  FolderOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  SearchOutlined,
  SettingOutlined,
  TagsOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logout } from '../../api/auth';
import { useSession } from '../../hooks/useSession';

const { Sider, Content } = Layout;

const items = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/packages', icon: <AppstoreOutlined />, label: '知识包' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/collections', icon: <FolderOutlined />, label: '集合' },
  { key: '/tags', icon: <TagsOutlined />, label: '标签' },
  { key: '/settings', icon: <SettingOutlined />, label: '设置' },
];

const mobileItems = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/packages', icon: <AppstoreOutlined />, label: '知识包' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/settings', icon: <UserOutlined />, label: '我的' },
];

const pageTitles: Array<[string, string]> = [
  ['/packages/upload', '上传知识包'],
  ['/packages', '知识包'],
  ['/search', '搜索'],
  ['/collections', '集合'],
  ['/tags', '标签'],
  ['/settings', '设置'],
  ['/', '首页'],
];

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const session = useSession();
  const searchRef = useRef<InputRef>(null);
  const [searchValue, setSearchValue] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.clear();
      navigate('/login', { replace: true });
    },
  });

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const selected =
    items
      .map((i) => i.key)
      .filter((k) => (k === '/' ? location.pathname === '/' : location.pathname.startsWith(k)))
      .sort((a, b) => b.length - a.length)[0] ?? '/';

  const mobileSelected =
    mobileItems.find((item) =>
      item.key === '/' ? location.pathname === '/' : location.pathname.startsWith(item.key),
    )?.key ?? '/';
  const title = pageTitles.find(([path]) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path),
  )?.[1];

  const submitSearch = () => {
    const query = searchValue.trim();
    navigate(query ? `/search?q=${encodeURIComponent(query)}` : '/search');
  };

  return (
    <Layout className="app-shell">
      <Sider className="desktop-sider" width={240} theme="light" trigger={null}>
        <div className="brand-block">
          <div className="brand-mark">K</div>
          <div>
            <Typography.Title level={4}>知识包仓库</Typography.Title>
            <Typography.Text>Personal archive</Typography.Text>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selected]}
          items={items}
          onClick={({ key }) => navigate(key)}
          className="side-menu"
        />
      </Sider>
      <Layout className="shell-main">
        <header className="topbar">
          <div className="mobile-brand">
            <div className="brand-mark">K</div>
            <strong>{title || '知识包仓库'}</strong>
          </div>
          <Input
            ref={searchRef}
            className="topbar-search"
            prefix={<SearchOutlined />}
            suffix={<kbd>⌘ K</kbd>}
            placeholder="搜索知识包、章节、标签"
            value={searchValue}
            onChange={(event) => setSearchValue(event.target.value)}
            onPressEnter={submitSearch}
            allowClear
          />
          <div className="topbar-actions">
            <Tooltip title="上传知识包">
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                onClick={() => navigate('/packages/upload')}
              >
                <span className="desktop-only">上传知识包</span>
              </Button>
            </Tooltip>
            <Dropdown
              open={mobileMenuOpen || undefined}
              onOpenChange={setMobileMenuOpen}
              menu={{
                items: [
                  { key: 'user', label: session.data?.display_name || session.data?.username, disabled: true },
                  { key: 'settings', icon: <SettingOutlined />, label: '系统设置' },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
                ],
                onClick: ({ key }) => {
                  if (key === 'settings') navigate('/settings');
                  if (key === 'logout') logoutMutation.mutate();
                },
              }}
              placement="bottomRight"
            >
              <Button className="user-button" type="text">
                <Avatar size={30}>{(session.data?.display_name || session.data?.username || 'U')[0]}</Avatar>
                <span className="desktop-only">{session.data?.display_name || session.data?.username}</span>
                <MenuOutlined className="mobile-only" />
              </Button>
            </Dropdown>
          </div>
        </header>
        <Content className="shell-content">
          <div className="content-inner">
            <Outlet />
          </div>
        </Content>
        <nav className="mobile-bottom-nav" aria-label="主要导航">
          {mobileItems.map((item) => (
            <button
              key={item.key}
              className={mobileSelected === item.key ? 'active' : ''}
              onClick={() => navigate(item.key)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </Layout>
    </Layout>
  );
}
