import { App, Avatar, Button, Dropdown, Input, Layout, Menu, Tooltip, Typography } from 'antd';
import type { InputRef, MenuProps } from 'antd';
import {
  AppstoreOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  FolderOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  SearchOutlined,
  SettingOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logout } from '../../api/auth';
import { getApiErrorMessage } from '../../api/client';
import { BRAND_MARK_PATH, BRAND_NAME, BRAND_TAGLINE } from '../../brand';
import { useSession } from '../../hooks/useSession';

const { Sider, Content } = Layout;
const searchShortcutLabel = /Mac|iPhone|iPad|iPod/i.test(navigator.userAgent) ? '⌘ K' : 'Ctrl K';

const primaryItems = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/packages', icon: <AppstoreOutlined />, label: '知识包' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/collections', icon: <FolderOutlined />, label: '集合' },
  { key: '/tags', icon: <TagsOutlined />, label: '标签' },
  { key: '/trash', icon: <DeleteOutlined />, label: '回收站' },
];

const mobileItems = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/packages', icon: <AppstoreOutlined />, label: '知识包' },
  { key: '/search', icon: <SearchOutlined />, label: '搜索' },
  { key: '/collections', icon: <FolderOutlined />, label: '集合' },
  { key: '/tags', icon: <TagsOutlined />, label: '标签' },
  { key: '/trash', icon: <DeleteOutlined />, label: '回收站' },
];

const pageTitles: Array<[string, string]> = [
  ['/packages/upload', '上传知识包'],
  ['/packages', '知识包'],
  ['/search', '搜索'],
  ['/collections', '集合'],
  ['/tags', '标签'],
  ['/trash', '回收站'],
  ['/settings', '设置'],
  ['/', '首页'],
];

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const session = useSession();
  const searchRef = useRef<InputRef>(null);
  const [searchValue, setSearchValue] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear();
      navigate('/login', { replace: true });
    },
    onError: (error) => message.error(getApiErrorMessage(error, '退出登录失败，请稍后重试')),
  });

  const isAdministrator = ['owner', 'admin'].includes(session.data?.role?.toLowerCase() || '');
  const canWriteContent = session.data?.role?.toLowerCase() !== 'viewer';
  const visiblePrimaryItems = canWriteContent
    ? primaryItems
    : primaryItems.filter((item) => item.key !== '/trash');
  const navigationItems = isAdministrator
    ? [...visiblePrimaryItems, { key: '/settings', icon: <SettingOutlined />, label: '设置' }]
    : visiblePrimaryItems;
  const mobileNavigationItems = canWriteContent
    ? mobileItems
    : mobileItems.filter((item) => item.key !== '/trash');

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
    navigationItems
      .map((i) => i.key)
      .filter((k) => (k === '/' ? location.pathname === '/' : location.pathname.startsWith(k)))
      .sort((a, b) => b.length - a.length)[0] ?? '/';

  const title = pageTitles.find(([path]) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path),
  )?.[1];

  const submitSearch = () => {
    const query = searchValue.trim();
    navigate(query ? `/search?q=${encodeURIComponent(query)}` : '/search');
  };

  const accountMenuItems: MenuProps['items'] = [
    { key: 'user', label: session.data?.display_name || session.data?.username, disabled: true },
    ...(isAdministrator
      ? [{ key: 'settings', icon: <SettingOutlined />, label: '系统设置' }]
      : []),
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: logoutMutation.isPending ? '正在退出' : '退出登录',
      danger: true,
      disabled: logoutMutation.isPending,
    },
  ];

  return (
    <Layout className="app-shell">
      <Sider className="desktop-sider" width={240} theme="light" trigger={null}>
        <div className="brand-block">
          <img className="brand-mark" src={BRAND_MARK_PATH} alt="" aria-hidden="true" />
          <div>
            <Typography.Title level={4}>{BRAND_NAME}</Typography.Title>
            <Typography.Text>{BRAND_TAGLINE}</Typography.Text>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selected]}
          items={navigationItems}
          onClick={({ key }) => navigate(key)}
          className="side-menu"
        />
      </Sider>
      <Layout className="shell-main">
        <header className="topbar">
          <div className="mobile-brand">
            <img className="brand-mark" src={BRAND_MARK_PATH} alt="" aria-hidden="true" />
            <strong>{title || BRAND_NAME}</strong>
          </div>
          <Input
            ref={searchRef}
            className="topbar-search"
            prefix={<SearchOutlined />}
            suffix={<kbd>{searchShortcutLabel}</kbd>}
            placeholder="搜索知识包、章节、标签"
            value={searchValue}
            onChange={(event) => setSearchValue(event.target.value)}
            onPressEnter={submitSearch}
            allowClear
          />
          <div className="topbar-actions">
            {canWriteContent ? <Tooltip title="上传知识包">
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                aria-label="上传知识包"
                onClick={() => navigate('/packages/upload')}
              >
                <span className="desktop-only">上传知识包</span>
              </Button>
            </Tooltip> : null}
            <Dropdown
              open={mobileMenuOpen}
              onOpenChange={setMobileMenuOpen}
              trigger={['click']}
              menu={{
                items: accountMenuItems,
                onClick: ({ key }) => {
                  if (key === 'settings') navigate('/settings');
                  if (key === 'logout') logoutMutation.mutate();
                },
              }}
              placement="bottomRight"
            >
              <Button className="user-button" type="text" aria-label="打开账号菜单">
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
          {mobileNavigationItems.map((item) => (
            <NavLink
              key={item.key}
              to={item.key}
              end={item.key === '/'}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              {item.icon}
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </Layout>
    </Layout>
  );
}
