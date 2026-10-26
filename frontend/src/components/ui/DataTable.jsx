import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { StatusBadge } from "./StatusBadge";
import { PaginationControls } from "./PaginationControls";
import { usePagination } from "../../hooks/usePagination";
import { formatBranch, formatDate, maskAccount } from "../../utils/formatters";

export function RequestTable({
  rows,
  actionBase = "/cheque-requests",
  actionLabel = "View",
  onAction,
  paginate = true,
}) {
  const pagination = usePagination(rows);
  const visibleRows = paginate ? pagination.paginatedItems : rows;

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[980px] text-center text-sm">
          <thead className="border-y border-slate-200 bg-slate-50 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3">Request ID</th>
              <th className="px-5 py-3">Customer Name</th>
              <th className="px-5 py-3">Account Number</th>
              <th className="px-5 py-3">Domicile Branch</th>
              <th className="px-5 py-3">Initiating Branch</th>
              <th className="px-5 py-3">Date</th>
              <th className="px-5 py-3">Status</th>
              <th className="px-5 py-3">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {visibleRows.map((row) => (
              <tr key={row.id} className="hover:bg-slate-50/80">
                <td className="whitespace-nowrap px-5 py-3.5 font-semibold text-ink">
                  {row.id}
                </td>
                <td className="px-5 py-3.5 text-slate-700">{row.customer}</td>
                <td className="whitespace-nowrap px-5 py-3.5 font-mono text-xs text-slate-600">
                  {maskAccount(row.accountNumber)}
                </td>
                <td className="px-5 py-3.5 text-slate-600">
                  {formatBranch(
                    row.accountHomeBranchName,
                    row.accountHomeBranchCode,
                    row.accountHomeBranch,
                  )}
                </td>
                <td className="px-5 py-3.5 text-slate-600">
                  {formatBranch(
                    row.servicingBranchName,
                    row.servicingBranchCode,
                    row.servicingBranch ?? row.branch,
                  )}
                </td>
                <td className="whitespace-nowrap px-5 py-3.5 text-slate-600">
                  {formatDate(row.createdAt)}
                </td>
                <td className="px-5 py-3.5">
                  <StatusBadge status={row.status} />
                </td>
                <td className="px-5 py-3.5">
                  {onAction ? (
                    <button
                      onClick={() => onAction(row)}
                      className="inline-flex items-center gap-1 font-semibold text-brand-700 hover:text-brand-600"
                    >
                      {actionLabel}
                      <ChevronRight className="h-4 w-4" />
                    </button>
                  ) : (
                    <Link
                      to={`${actionBase}/${row.id}`}
                      className="inline-flex items-center gap-1 font-semibold text-brand-700 hover:text-brand-600"
                    >
                      {actionLabel}
                      <ChevronRight className="h-4 w-4" />
                    </Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {paginate && (
        <PaginationControls
          {...pagination}
          onPageChange={pagination.setCurrentPage}
        />
      )}
    </div>
  );
}
