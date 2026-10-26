import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../providers/AuthProvider';
import { hasPermission } from '../../constants/access';
import { LoadingState } from '../../components/ui/PageState';

export function ProtectedRoute({ permission }) {
  const { isAuthenticated, user, initializing } = useAuth();
  const location = useLocation();
  if (initializing) return <LoadingState label="Restoring secure session…" />;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location }} />;
  if (permission && !hasPermission(user, permission)) return <Navigate to="/unauthorized" replace />;
  return <Outlet />;
}
