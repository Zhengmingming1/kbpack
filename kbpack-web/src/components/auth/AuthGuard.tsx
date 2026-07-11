import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { LoadingBlock } from '../common/QueryState';
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
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?from=${encodeURIComponent(from)}`} replace />;
  }

  return <Outlet />;
}
