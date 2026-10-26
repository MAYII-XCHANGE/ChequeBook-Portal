import { Navigate, Route, Routes } from "react-router-dom";
import { PERMISSIONS } from "../../constants/access";
import { AppShell } from "../../layouts/AppShell";
import { ApprovalsPage } from "../../features/approvals/ApprovalsPage";
import { AuditPage } from "../../features/audit/AuditPage";
import { LoginPage } from "../../features/auth/LoginPage";
import { DashboardPage } from "../../features/dashboard/DashboardPage";
import { ProcessingPage } from "../../features/processing/ProcessingPage";
import { ProfilePage } from "../../features/profile/ProfilePage";
import { ReportsPage } from "../../features/reports/ReportsPage";
import { NewRequestPage } from "../../features/requests/NewRequestPage";
import { RequestDetailPage } from "../../features/requests/RequestDetailPage";
import { RequestListPage } from "../../features/requests/RequestListPage";
import {
  NotFoundPage,
  UnauthorizedPage,
} from "../../features/system/SystemPages";
import { BranchManagementPage } from "../../features/administration/BranchManagementPage";
import { RoleManagementPage } from "../../features/administration/RoleManagementPage";
import { UserManagementPage } from "../../features/administration/UserManagementPage";
import { VendorExportsPage } from "../../features/exports/VendorExportsPage";
import { ProtectedRoute } from "./ProtectedRoute";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="unauthorized" element={<UnauthorizedPage />} />
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.VIEW_CHEQUE_REQUEST} />
            }
          >
            <Route path="cheque-requests" element={<RequestListPage />} />
            <Route
              path="cheque-requests/:requestId"
              element={<RequestDetailPage />}
            />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.CREATE_CHEQUE_REQUEST} />
            }
          >
            <Route path="cheque-requests/new" element={<NewRequestPage />} />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.APPROVE_CHEQUE_REQUEST} />
            }
          >
            <Route path="approvals" element={<ApprovalsPage />} />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.PROCESS_CHEQUE_REQUEST} />
            }
          >
            <Route path="processing" element={<ProcessingPage />} />
          </Route>
          <Route
            element={<ProtectedRoute permission={PERMISSIONS.EXPORT_VENDOR} />}
          >
            <Route path="vendor-exports" element={<VendorExportsPage />} />
          </Route>
          <Route
            element={<ProtectedRoute permission={PERMISSIONS.VIEW_REPORTS} />}
          >
            <Route path="reports" element={<ReportsPage />} />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.VIEW_AUDIT_TRAIL} />
            }
          >
            <Route path="audit-trail" element={<AuditPage />} />
          </Route>
          <Route
            path="administration"
            element={<Navigate to="/administration/users" replace />}
          />
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.USER_MANAGEMENT} />
            }
          >
            <Route
              path="administration/users"
              element={<UserManagementPage />}
            />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.ROLE_MANAGEMENT} />
            }
          >
            <Route
              path="administration/roles"
              element={<RoleManagementPage />}
            />
          </Route>
          <Route
            element={
              <ProtectedRoute permission={PERMISSIONS.BRANCH_MANAGEMENT} />
            }
          >
            <Route
              path="administration/branches"
              element={<BranchManagementPage />}
            />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
