import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../app/providers/AuthProvider', () => ({
  useAuth: (() => {
    const user = { id: 42, role: 'BOM', branchCode: '03' };
    return () => ({ user });
  })(),
}));

vi.mock('../services/api/notificationApi', () => ({
  notificationApi: { summary: vi.fn() },
}));

import { notificationApi } from '../services/api/notificationApi';
import {
  NOTIFICATION_REFRESH_EVENT,
  NotificationProvider,
  useNotificationSummary,
} from './useNotificationSummary';

function SummaryProbe() {
  const { totalCount } = useNotificationSummary();
  return <p>Pending total: {totalCount}</p>;
}

describe('NotificationProvider', () => {
  beforeEach(() => {
    notificationApi.summary.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads the summary and refreshes immediately when a workflow action completes', async () => {
    notificationApi.summary
      .mockResolvedValueOnce({ totalCount: 1, actions: [], generatedAt: '2026-09-14T08:00:00Z' })
      .mockResolvedValueOnce({ totalCount: 0, actions: [], generatedAt: '2026-09-14T08:01:00Z' });

    render(
      <NotificationProvider>
        <SummaryProbe />
      </NotificationProvider>,
    );

    expect(await screen.findByText('Pending total: 1')).toBeInTheDocument();
    window.dispatchEvent(new Event(NOTIFICATION_REFRESH_EVENT));
    expect(await screen.findByText('Pending total: 0')).toBeInTheDocument();
    expect(notificationApi.summary).toHaveBeenCalledTimes(2);
  });

  it('keeps the application usable when the summary request fails', async () => {
    notificationApi.summary.mockRejectedValue(new Error('Service unavailable'));

    render(
      <NotificationProvider>
        <SummaryProbe />
      </NotificationProvider>,
    );

    expect(screen.getByText('Pending total: 0')).toBeInTheDocument();
    await waitFor(() => expect(notificationApi.summary).toHaveBeenCalledOnce());
    expect(screen.queryByText(/service unavailable/i)).not.toBeInTheDocument();
  });

  it('cleans up visible-tab polling when the provider unmounts', async () => {
    notificationApi.summary.mockResolvedValue({ totalCount: 0, actions: [], generatedAt: null });
    const setIntervalSpy = vi.spyOn(window, 'setInterval');
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');

    const view = render(
      <NotificationProvider>
        <SummaryProbe />
      </NotificationProvider>,
    );

    await waitFor(() => expect(notificationApi.summary).toHaveBeenCalledOnce());
    const pollingId = setIntervalSpy.mock.results[0]?.value;
    expect(pollingId).toBeDefined();

    view.unmount();
    expect(clearIntervalSpy).toHaveBeenCalledWith(pollingId);
  });
});
