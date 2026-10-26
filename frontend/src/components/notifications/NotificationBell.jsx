import { Bell, ChevronRight } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  formatNotificationCount,
  NOTIFICATION_ACTIONS,
} from "../../constants/notificationActions";
import { useNotificationSummary } from "../../hooks/useNotificationSummary";

export function NotificationBell() {
  const { totalCount, actions } = useNotificationSummary();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const navigate = useNavigate();
  const availableActions = useMemo(
    () =>
      actions
        .filter((action) => action.count > 0 && NOTIFICATION_ACTIONS[action.code])
        .map((action) => ({ ...action, ...NOTIFICATION_ACTIONS[action.code] })),
    [actions],
  );

  useEffect(() => {
    if (!open) return undefined;
    const handlePointerDown = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  const selectAction = (path) => {
    setOpen(false);
    navigate(path);
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        className="relative grid h-11 w-11 place-items-center rounded-md border border-slate-200 text-slate-600 transition-colors hover:bg-slate-50"
        onClick={() => setOpen((current) => !current)}
        aria-label={`Notifications${totalCount ? `, ${totalCount} pending action${totalCount === 1 ? "" : "s"}` : ""}`}
        aria-expanded={open}
        aria-controls="notification-action-menu"
      >
        <Bell className="h-5 w-5" />
        {totalCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 min-w-5 rounded-full bg-red-600 px-1.5 py-0.5 text-center text-[10px] font-bold leading-4 text-white ring-2 ring-white">
            {formatNotificationCount(totalCount)}
          </span>
        )}
      </button>

      {open && (
        <div
          id="notification-action-menu"
          className="absolute right-0 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl"
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-ink">Pending actions</p>
            <p className="mt-0.5 text-xs text-slate-500">
              Tasks currently assigned to your role
            </p>
          </div>
          {availableActions.length ? (
            <div className="p-1.5">
              {availableActions.map((action) => (
                <button
                  key={action.code}
                  type="button"
                  onClick={() => selectAction(action.path)}
                  className="flex w-full items-center gap-3 rounded-md px-3 py-3 text-left hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-200"
                >
                  <span className="grid h-8 min-w-8 place-items-center rounded-full bg-brand-50 px-2 text-xs font-bold text-brand-700">
                    {formatNotificationCount(action.count)}
                  </span>
                  <span className="flex-1 text-sm font-semibold text-slate-700">
                    {action.label}
                  </span>
                  <ChevronRight className="h-4 w-4 text-slate-400" />
                </button>
              ))}
            </div>
          ) : (
            <p className="px-4 py-6 text-center text-sm text-slate-500">
              You have no pending actions.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
