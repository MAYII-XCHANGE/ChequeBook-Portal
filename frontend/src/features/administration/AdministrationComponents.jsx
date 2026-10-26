import { EmptyState } from "../../components/ui/PageState";

export { PaginationControls } from "../../components/ui/PaginationControls";

export function ManagementTable({ headers, rows, minWidth = "760px" }) {
  if (!rows.length) {
    return (
      <EmptyState
        title="No records found"
        description="Create the first record to get started."
      />
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm" style={{ minWidth }}>
        <thead className="border-y border-slate-200 bg-slate-50 text-[11px] font-extrabold uppercase tracking-wide text-slate-500">
          <tr>
            {headers.map((header) => (
              <th key={header} className="px-5 py-3">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map(({ id, cells }) => (
            <tr key={id} className="hover:bg-slate-50/70">
              {cells.map((value, index) => (
                <td
                  key={`${id}-${headers[index]}`}
                  className="px-5 py-3.5 leading-5 text-slate-700"
                >
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function StatusPill({ active }) {
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ${
        active
          ? "bg-emerald-50 text-emerald-700"
          : "bg-slate-100 text-slate-600"
      }`}
    >
      {active ? "Active" : "Inactive"}
    </span>
  );
}
