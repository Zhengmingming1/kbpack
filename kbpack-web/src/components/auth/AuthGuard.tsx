import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { isUnauthorizedError } from '../../api/client';
import { ErrorBlock, LoadingBlock } from '../common/QueryState';
import { useSession } from '../../hooks/useSession';

export function AuthGuard() {
  const location = useLocation();
  const session = useSession();

  if (session.isPending) {
    return (
      <main className="fullscreen-state">
        <LoadingBlock rows={3} />
      </main>
    );
  }

  if (session.isError) {
    if (!isUnauthorizedError(session.error)) {
      return (
        <main className="fullscreen-state">
          <ErrorBlock
            title="暂时无法确认登录状态"
            description="服务可能正在启动或网络暂时不可用，请稍后重试。"
            onRetry={() => void session.refetch()}
          />
        </main>
      );
    }
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?from=${encodeURIComponent(from)}`} replace />;
  }

  return <Outlet />;
}

export function AdministratorGuard({ children }: { children: ReactNode }) {
  const session = useSession();
  const role = session.data?.role?.toLowerCase();

  if (session.isPending) return <LoadingBlock rows={3} />;
  if (role !== 'owner' && role !== 'admin') return <Navigate to="/" replace />;
  return <>{children}</>;
}

export function ContentWriterGuard({ children }: { children: ReactNode }) {
  const session = useSession();
  const role = session.data?.role?.toLowerCase();

  if (session.isPending) return <LoadingBlock rows={3} />;
  if (role === 'viewer') return <Navigate to="/" replace />;
  return <>{children}</>;
}
