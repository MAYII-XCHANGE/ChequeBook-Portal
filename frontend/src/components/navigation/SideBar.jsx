import { LoaderCircle, LogOut, X } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";
import { ROLE_LABELS } from "../../constants/access";
import { notificationCountForPath, formatNotificationCount } from "../../constants/notificationActions";
import { useNotificationSummary } from "../../hooks/useNotificationSummary";
import { initials } from "../../utils/formatters";
import { Logo } from "../brand/Logo";
import { getMenuItemsForUser } from "./menuItems";

export function SideBar({
  user,
  onClose,
  onLogout,
  loggingOut = false,
  mobile = false,
}) {
  const location = useLocation();
  const menuItems = getMenuItemsForUser(user);
  const { actions } = useNotificationSummary();

  return (
    <>
      <div className="flex h-24 items-center justify-between border-b border-white/15 px-6">
        <Logo inverse />
        {mobile && (
          <button
            type="button"
            className="rounded-lg p-2 text-white hover:bg-white/10"
            onClick={onClose}
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" />
          </button>
        )}
      </div>

      <nav
        className="flex-1 overflow-y-auto px-5 py-8"
        aria-label={mobile ? "Mobile navigation" : "Main navigation"}
      >
        <div className="space-y-2">
          {menuItems.map(({ label, to, icon: Icon }) => {
            const isNewRequestRoute = location.pathname === "/cheque-requests/new";
            const notificationCount = notificationCountForPath(actions, to);
            return (
              <NavLink
                key={to}
                to={to}
                onClick={onClose}
                className={({ isActive }) => {
                  const showActive =
                    isActive && !(to === "/cheque-requests" && isNewRequestRoute);
                  return `flex min-h-12 items-center gap-3 rounded-[10px] px-3.5 py-3 text-sm font-semibold transition-colors ${
                    showActive
                      ? "bg-white text-brand-700 shadow-sm"
                      : "text-white hover:bg-white/10"
                  }`;
                }}
              >
                <Icon className="h-[18px] w-[18px] shrink-0" />
                <span className="flex-1">{label}</span>
                {notificationCount > 0 && (
                  <span className="min-w-6 rounded-full bg-red-500 px-1.5 py-0.5 text-center text-[10px] font-bold leading-4 text-white">
                    {formatNotificationCount(notificationCount)}
                  </span>
                )}
              </NavLink>
            );
          })}
        </div>
      </nav>

      <div className="border-t border-white/15 p-5">
        <div
          aria-label="Sidebar user identity"
          className="flex items-center gap-3 rounded-[10px] bg-white/10 p-3"
        >
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand-500 text-xs font-bold text-white">
            {initials(user.name)}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-white">{user.name}</p>
            <p className="mt-0.5 text-[11px] leading-4 text-white/80">
              {ROLE_LABELS[user.role]}
              {user.branch ? ` | ${user.branch}` : ""}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onLogout}
          disabled={loggingOut}
          className="mt-3 flex min-h-11 w-full items-center justify-center gap-2 rounded-[10px] border border-white/15 px-3 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loggingOut ? (
            <LoaderCircle className="h-[18px] w-[18px] animate-spin" aria-hidden="true" />
          ) : (
            <LogOut className="h-[18px] w-[18px]" aria-hidden="true" />
          )}
          {loggingOut ? "Signing out…" : "Sign out"}
        </button>
      </div>
    </>
  );
}
