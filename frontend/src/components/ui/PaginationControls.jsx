import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./Button";

export function PaginationControls({
  currentPage,
  pageSize,
  totalItems,
  totalPages,
  onPageChange,
}) {
  if (!totalItems || totalPages <= 1) return null;

  const first = (currentPage - 1) * pageSize + 1;
  const last = Math.min(currentPage * pageSize, totalItems);

  return (
    <div className="flex flex-col gap-3 border-t border-slate-100 px-5 py-4 text-sm sm:flex-row sm:items-center sm:justify-between">
      <span className="text-xs font-medium text-slate-500">
        Showing {first}–{last} of {totalItems}
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          className="min-h-9 px-3"
          disabled={currentPage === 1}
          onClick={() => onPageChange(currentPage - 1)}
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span className="min-w-20 text-center text-xs font-semibold text-slate-600">
          {currentPage} of {totalPages}
        </span>
        <Button
          variant="secondary"
          className="min-h-9 px-3"
          disabled={currentPage === totalPages}
          onClick={() => onPageChange(currentPage + 1)}
          aria-label="Next page"
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
