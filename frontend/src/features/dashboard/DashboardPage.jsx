import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  FileCheck2,
  FileText,
  Plus,
  RefreshCw,
  Truck,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../../app/providers/AuthProvider";
import { AdminDashboard } from "../administration/AdminDashboard";
import { Card, CardHeader } from "../../components/ui/Card";
import { PageHeader } from "../../components/ui/PageHeader";
import { ErrorState, LoadingState } from "../../components/ui/PageState";
import { RequestTable } from "../../components/ui/DataTable";
import {
  getRequestScope,
  hasPermission,
  PERMISSIONS,
  ROLE_LABELS,
} from "../../constants/access";
import { REQUEST_STATUS } from "../../constants/status";
import { chequeRequestApi } from "../../services/api/chequeRequestApi";
import { useAsync } from "../../hooks/useAsync";
import { useLocalTimeGreeting } from "../../hooks/useLocalTimeGreeting";

const metricConfig = [
  {
    label: "Total Requests",
    statuses: null,
    icon: FileText,
    tone: "bg-blue-50 text-blue-700",
  },
  {
    label: "Pending Approval",
    statuses: [REQUEST_STATUS.PENDING_APPROVAL],
    icon: Clock3,
    tone: "bg-amber-50 text-amber-700",
  },
  {
    label: "In Process",
    statuses: [
      REQUEST_STATUS.DEBIT_PENDING,
      REQUEST_STATUS.DEBIT_FAILED,
      REQUEST_STATUS.DEBIT_REVIEW_REQUIRED,
      REQUEST_STATUS.CHEQUEBOOK_REQUEST_FAILED,
      REQUEST_STATUS.REVERSAL_REVIEW_REQUIRED,
      REQUEST_STATUS.EXPORTED,
      REQUEST_STATUS.SENT_TO_VENDOR,
      REQUEST_STATUS.PRINTED,
      REQUEST_STATUS.FINACLE_REGISTRATION_PENDING,
      REQUEST_STATUS.FINACLE_REGISTERED,
      REQUEST_STATUS.FINACLE_REGISTRATION_FAILED,
      REQUEST_STATUS.FINACLE_REGISTRATION_REVIEW_REQUIRED,
      REQUEST_STATUS.DISPATCHED,
      REQUEST_STATUS.RECEIVED_BY_BRANCH,
    ],
    icon: RefreshCw,
    tone: "bg-violet-50 text-violet-700",
  },
  {
    label: "Approved",
    statuses: [REQUEST_STATUS.APPROVED],
    icon: FileCheck2,
    tone: "bg-emerald-50 text-emerald-700",
  },
  {
    label: "Completed",
    statuses: [REQUEST_STATUS.DELIVERED, REQUEST_STATUS.CUSTODY_CONFIRMED],
    icon: Truck,
    tone: "bg-teal-50 text-teal-700",
  },
];

export function DashboardPage() {
  const { user } = useAuth();
  const greeting = useLocalTimeGreeting();
  if (user.role === "ADMIN") return <AdminDashboard user={user} greeting={greeting} />;
  return <OperationalDashboard user={user} greeting={greeting} />;
}

function OperationalDashboard({ user, greeting }) {
  const { data, loading, error, retry } = useAsync(
    () => hasPermission(user, PERMISSIONS.VIEW_CHEQUE_REQUEST) ? chequeRequestApi.getRequests({ ...getRequestScope(user), pageSize: 100 }) : Promise.resolve({ data: [], total: 0 }),
    [user.role, user.branch],
  );
  const rows = data?.data || [];
  return (
    <>
      <PageHeader
        title={`${greeting}, ${user.name.split(" ")[0]}`}
        description="Here is the current view of chequebook request activity in your authorized scope."
      />
      {loading ? (
        <Card>
          <LoadingState label="Preparing your dashboard…" />
        </Card>
      ) : error ? (
        <Card>
          <ErrorState message={error.message} onRetry={retry} />
        </Card>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            {metricConfig.map(({ label, statuses, icon: Icon, tone }) => {
              const value = statuses
                ? rows.filter((item) => statuses.includes(item.status)).length
                : rows.length;
              return (
                <Card key={label} className="p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-sm font-medium text-slate-500">
                        {label}
                      </p>
                      <p className="mt-3 text-3xl font-bold tracking-tight text-ink">
                        {value}
                      </p>
                    </div>
                    <span
                      className={`grid h-10 w-10 place-items-center rounded-md ${tone}`}
                    >
                      <Icon className="h-5 w-5" />
                    </span>
                  </div>
                </Card>
              );
            })}
          </div>
          <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_300px]">
            <Card>
              <CardHeader
                title="Recent requests"
                description="Latest activity across your request scope"
                action={
                  <Link
                    to="/cheque-requests"
                    className="inline-flex items-center gap-1 text-sm font-semibold text-brand-700"
                  >
                    View all <ArrowRight className="h-4 w-4" />
                  </Link>
                }
              />
              <RequestTable rows={rows.slice(0, 10)} />
            </Card>
            <div className="space-y-6">
              <Card>
                <CardHeader title="Quick actions" />
                <div className="space-y-3 p-4">
                  {hasPermission(
                    user,
                    PERMISSIONS.CREATE_CHEQUE_REQUEST,
                  ) && (
                    <Link
                      to="/cheque-requests/new"
                      className="group flex items-center gap-3 rounded-md border border-brand-100 bg-brand-50 p-3.5"
                    >
                      <span className="grid h-9 w-9 place-items-center rounded bg-brand-600 text-white">
                        <Plus className="h-5 w-5" />
                      </span>
                      <span>
                        <span className="block text-sm font-semibold text-ink">
                          New cheque request
                        </span>
                        <span className="block text-xs text-slate-500">
                          Search customer account
                        </span>
                      </span>
                      <ArrowRight className="ml-auto h-4 w-4 text-brand-600 group-hover:translate-x-0.5" />
                    </Link>
                  )}
                  <Link
                    to="/cheque-requests"
                    className="group flex items-center gap-3 rounded-md border border-slate-200 p-3.5 hover:bg-slate-50"
                  >
                    <span className="grid h-9 w-9 place-items-center rounded bg-slate-100 text-slate-600">
                      <FileText className="h-5 w-5" />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-ink">
                        View all requests
                      </span>
                      <span className="block text-xs text-slate-500">
                        Search and track status
                      </span>
                    </span>
                    <ArrowRight className="ml-auto h-4 w-4 text-slate-400" />
                  </Link>
                </div>
              </Card>
            </div>
          </div>
        </>
      )}
    </>
  );
}
