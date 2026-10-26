import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useAuth } from "../app/providers/AuthProvider";
import { notificationApi } from "../services/api/notificationApi";

export const NOTIFICATION_REFRESH_EVENT = "portal:notifications:refresh";
export const NOTIFICATION_POLL_INTERVAL_MS = 60_000;

const EMPTY_SUMMARY = { totalCount: 0, actions: [], generatedAt: null };
const NotificationContext = createContext(null);

export function requestNotificationRefresh() {
  window.dispatchEvent(new Event(NOTIFICATION_REFRESH_EVENT));
}

export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const [summary, setSummary] = useState(EMPTY_SUMMARY);
  const inFlight = useRef(null);
  const activeUserId = useRef(user?.id ?? null);

  useEffect(() => {
    activeUserId.current = user?.id ?? null;
    if (!user) setSummary(EMPTY_SUMMARY);
  }, [user]);

  const refresh = useCallback(() => {
    if (!user?.id) return Promise.resolve();
    if (inFlight.current) return inFlight.current;

    const requestedUserId = user.id;
    const request = notificationApi
      .summary(user)
      .then((result) => {
        if (activeUserId.current === requestedUserId) setSummary(result);
        return result;
      })
      .catch(() => undefined)
      .finally(() => {
        if (inFlight.current === request) inFlight.current = null;
      });

    inFlight.current = request;
    return request;
  }, [user]);

  useEffect(() => {
    if (!user?.id) return undefined;

    let intervalId;
    const stopPolling = () => {
      if (intervalId) window.clearInterval(intervalId);
      intervalId = undefined;
    };
    const startPolling = () => {
      stopPolling();
      if (document.visibilityState === "visible") {
        intervalId = window.setInterval(refresh, NOTIFICATION_POLL_INTERVAL_MS);
      }
    };
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        refresh();
        startPolling();
      } else {
        stopPolling();
      }
    };

    refresh();
    startPolling();
    document.addEventListener("visibilitychange", handleVisibility);
    window.addEventListener(NOTIFICATION_REFRESH_EVENT, refresh);

    return () => {
      stopPolling();
      document.removeEventListener("visibilitychange", handleVisibility);
      window.removeEventListener(NOTIFICATION_REFRESH_EVENT, refresh);
    };
  }, [refresh, user?.id]);

  const value = useMemo(
    () => ({
      ...summary,
      refresh,
      countFor(code) {
        return summary.actions.find((action) => action.code === code)?.count || 0;
      },
    }),
    [refresh, summary],
  );

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotificationSummary() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error("useNotificationSummary must be used inside NotificationProvider");
  }
  return context;
}
