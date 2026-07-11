import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { AuthGuard } from '../components/auth/AuthGuard';
import { LoadingBlock } from '../components/common/QueryState';

const LoginPage = lazy(() => import('../pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const HomePage = lazy(() => import('../pages/HomePage').then((module) => ({ default: module.HomePage })));
const PackageListPage = lazy(() => import('../pages/PackageListPage').then((module) => ({ default: module.PackageListPage })));
const UploadPage = lazy(() => import('../pages/UploadPage').then((module) => ({ default: module.UploadPage })));
const PackageDetailPage = lazy(() => import('../pages/PackageDetailPage').then((module) => ({ default: module.PackageDetailPage })));
const PreviewPage = lazy(() => import('../pages/PreviewPage').then((module) => ({ default: module.PreviewPage })));
const ReaderPage = lazy(() => import('../pages/ReaderPage').then((module) => ({ default: module.ReaderPage })));
const SearchPage = lazy(() => import('../pages/SearchPage').then((module) => ({ default: module.SearchPage })));
const TagManagePage = lazy(() => import('../pages/TagManagePage').then((module) => ({ default: module.TagManagePage })));
const CollectionManagePage = lazy(() => import('../pages/CollectionManagePage').then((module) => ({ default: module.CollectionManagePage })));
const SettingsPage = lazy(() => import('../pages/SettingsPage').then((module) => ({ default: module.SettingsPage })));

function page(element: ReactNode) {
  return <Suspense fallback={<LoadingBlock rows={5} />}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: page(<LoginPage />),
  },
  {
    element: <AuthGuard />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { index: true, element: page(<HomePage />) },
          { path: 'packages', element: page(<PackageListPage />) },
          { path: 'packages/upload', element: page(<UploadPage />) },
          { path: 'packages/:packageId', element: page(<PackageDetailPage />) },
          { path: 'search', element: page(<SearchPage />) },
          { path: 'tags', element: page(<TagManagePage />) },
          { path: 'collections', element: page(<CollectionManagePage />) },
          { path: 'settings', element: page(<SettingsPage />) },
        ],
      },
      {
        path: '/packages/:packageId/preview/:versionId',
        element: page(<PreviewPage />),
      },
      { path: '/documents/:documentId', element: page(<ReaderPage />) },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
