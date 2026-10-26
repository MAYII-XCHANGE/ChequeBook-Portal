import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  ChevronDown,
  LogOut,
  Menu,
  UserRound,
} from "lucide-react";
import { useAuth } from "../app/providers/AuthProvider";
import { SideBar } from "../components/navigation/SideBar";
import { NotificationBell } from "../components/notifications/NotificationBell";
import { ROLE_LABELS } from "../constants/access";
import { initials } from "../utils/formatters";

export function AppShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    setMobileOpen(false);
    setProfileOpen(false);
    try {
      await logout();
    } catch {
      // AuthProvider still clears the local session when the server is unavailable.
    } finally {
      navigate("/login", { replace: true });
    }
  };
  return (
    <div className="min-h-screen bg-[#F5F7FA]">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-72 flex-col bg-ink lg:flex">
        <SideBar
          user={user}
          onLogout={handleLogout}
          loggingOut={loggingOut}
        />
      </aside>
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            className="absolute inset-0 bg-slate-950/60"
            aria-label="Close navigation"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="absolute inset-y-0 right-0 flex w-[min(88vw,320px)] flex-col rounded-l-3xl bg-ink shadow-2xl">
            <SideBar
              user={user}
              mobile
              onClose={() => setMobileOpen(false)}
              onLogout={handleLogout}
              loggingOut={loggingOut}
            />
          </aside>
        </div>
      )}
      <div className="lg:pl-72">
        <header className="sticky top-0 z-30 flex h-20 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <button
              className="rounded-md border border-slate-200 p-2 text-slate-600 lg:hidden"
              onClick={() => setMobileOpen(true)}
              aria-label="Open navigation"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Cheque operations
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <div className="relative">
              <button
                className="flex items-center gap-3 rounded-md p-1.5 text-left hover:bg-slate-50"
                onClick={() => setProfileOpen(!profileOpen)}
                aria-expanded={profileOpen}
              >
                <span className="grid h-9 w-9 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">
                  {initials(user.name)}
                </span>
                <span className="hidden sm:block">
                  <span className="block text-sm font-semibold text-ink">
                    {user.name}
                  </span>
                  <span
                    aria-label="User role and branch"
                    className="mt-0.5 block text-xs text-slate-500"
                  >
                    {ROLE_LABELS[user.role]}{" "}
                    <span className="px-1 text-slate-300">|</span> {user.branch}
                  </span>
                </span>
                <ChevronDown className="hidden h-4 w-4 text-slate-400 sm:block" />
              </button>
              {profileOpen && (
                <div className="absolute right-0 mt-2 w-56 rounded-lg border border-slate-200 bg-white p-1.5 shadow-xl">
                  <NavLink
                    to="/profile"
                    onClick={() => setProfileOpen(false)}
                    className="flex items-center gap-2 rounded px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
                  >
                    <UserRound className="h-4 w-4" />
                    Profile
                  </NavLink>
                  <button
                    type="button"
                    onClick={handleLogout}
                    disabled={loggingOut}
                    className="flex w-full items-center gap-2 rounded px-3 py-2 text-sm text-red-600 hover:bg-red-50"
                  >
                    <LogOut className="h-4 w-4" />
                    {loggingOut ? "Signing out…" : "Sign out"}
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main
          key={location.pathname}
          className="mx-auto max-w-[1500px] p-4 sm:p-6 lg:p-8"
        >
          <Outlet />
        </main>
      </div>
    </div>
  );
}
