import { Plus, Search } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../app/providers/AuthProvider";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { RequestTable } from "../../components/ui/DataTable";
import { inputClass } from "../../components/ui/FormField";
import { PageHeader } from "../../components/ui/PageHeader";
import { PaginationControls } from "../../components/ui/PaginationControls";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "../../components/ui/PageState";
import {
  getRequestScope,
  hasPermission,
  PERMISSIONS,
} from "../../constants/access";
import { STATUS_CONFIG } from "../../constants/status";
import { useAsync } from "../../hooks/useAsync";
import { TABLE_PAGE_SIZE } from "../../hooks/usePagination";
import { chequeRequestApi } from "../../services/api/chequeRequestApi";

export function RequestListPage() {
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [branch, setBranch] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(1);
  const scopedBranch = getRequestScope(user).branch || "";
  const { data, loading, error, retry } = useAsync(
    () =>
      chequeRequestApi.getRequests({
        search,
        status,
        branch: scopedBranch || branch,
        from: fromDate,
        sort,
        page: page - 1,
        pageSize: TABLE_PAGE_SIZE,
      }),
    [user.role, user.branchCode, search, status, branch, fromDate, sort, page],
  );
  const rows = data?.data || [];
  const total = data?.total || 0;
  const maxPage = Math.max(1, Math.ceil(total / TABLE_PAGE_SIZE));
  const branches = [
    ...new Map(
      rows
        .filter((item) => item.branchCode)
        .map((item) => [item.branchCode, item.branch]),
    ).entries(),
  ];
  const changeFilters = (setter) => (event) => {
    setter(event.target.value);
    setPage(1);
  };
  return (
    <>
      <PageHeader
        eyebrow="Request tracking"
        title="Cheque requests"
        description="Search, review and follow requests throughout their lifecycle."
        action={
          hasPermission(user, PERMISSIONS.CREATE_CHEQUE_REQUEST) && (
            <Link to="/cheque-requests/new">
              <Button>
                <Plus className="h-4 w-4" />
                New request
              </Button>
            </Link>
          )
        }
      />
      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 sm:grid-cols-2 xl:grid-cols-[minmax(260px,1.7fr)_1fr_1fr_1fr_1fr]">
          <div className="relative">
            <Search className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
            <input
              aria-label="Search requests"
              className={`${inputClass} pl-10`}
              placeholder="Search requests"
              value={search}
              onChange={changeFilters(setSearch)}
            />
          </div>
          <select
            aria-label="Filter by status"
            className={inputClass}
            value={status}
            onChange={changeFilters(setStatus)}
          >
            <option value="">All statuses</option>
            {Object.entries(STATUS_CONFIG).map(([value, config]) => (
              <option key={value} value={value}>
                {config.label}
              </option>
            ))}
          </select>
          <select
            aria-label="Filter by branch"
            className={inputClass}
            value={scopedBranch || branch}
            disabled={Boolean(scopedBranch)}
            onChange={changeFilters(setBranch)}
          >
            <option value="">All branches</option>
            {scopedBranch && (
              <option value={scopedBranch}>{user.branch}</option>
            )}
            {!scopedBranch &&
              branches.map(([code, name]) => (
                <option key={code} value={code}>
                  {name} - {code}
                </option>
              ))}
          </select>
          <input
            type="date"
            aria-label="Created from"
            className={inputClass}
            value={fromDate}
            onChange={changeFilters(setFromDate)}
          />
          <select
            aria-label="Sort requests"
            className={inputClass}
            value={sort}
            onChange={changeFilters(setSort)}
          >
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
            <option value="id">Request ID</option>
          </select>
        </div>
        {loading ? (
          <LoadingState label="Loading requests…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={retry} />
        ) : !rows.length ? (
          <EmptyState title="No cheque requests found" />
        ) : (
          <RequestTable rows={rows} paginate={false} />
        )}
        {!loading && !error && total > 0 && (
          <PaginationControls
            currentPage={page}
            pageSize={TABLE_PAGE_SIZE}
            totalItems={total}
            totalPages={maxPage}
            onPageChange={setPage}
          />
        )}
      </Card>
    </>
  );
}
