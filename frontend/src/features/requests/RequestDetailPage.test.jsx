import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { REQUEST_STATUS } from '../../constants/status';

vi.mock('../../app/providers/AuthProvider', () => ({
  useAuth: () => ({
    user: {
      role: 'CSO',
      branchCode: '001',
      permissions: ['DELIVER_CHEQUE_REQUEST'],
    },
  }),
}));

vi.mock('../../services/api/chequeRequestApi', () => ({
  chequeRequestApi: {
    getRequestById: vi.fn(),
    confirmBranchReceipt: vi.fn(),
    markDelivered: vi.fn(),
  },
}));

vi.mock('../../hooks/useNotificationSummary', () => ({
  requestNotificationRefresh: vi.fn(),
}));

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

import { chequeRequestApi } from '../../services/api/chequeRequestApi';
import { RequestDetailPage } from './RequestDetailPage';

const request = (status) => ({
  id: 'CBR-2026-000118',
  status,
  version: 6,
  customer: 'Olumide Akinola',
  accountNumber: '1234564821',
  branch: 'Head Office',
  branchCode: '001',
  servicingBranch: 'Head Office',
  servicingBranchName: 'Head Office',
  servicingBranchCode: '001',
  accountHomeBranch: 'Head Office',
  accountHomeBranchName: 'Head Office',
  accountHomeBranchCode: '001',
  createdAt: '2026-10-14T08:00:00Z',
  createdBy: 'Branch CSO',
  leafCount: 50,
  timeline: [],
});

const renderPage = (status) => {
  chequeRequestApi.getRequestById.mockResolvedValue({ request: request(status) });
  return render(
    <MemoryRouter initialEntries={['/cheque-requests/CBR-2026-000118']}>
      <Routes>
        <Route path="/cheque-requests/:requestId" element={<RequestDetailPage />} />
        <Route path="/cheque-requests" element={<p>Request list</p>} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('RequestDetailPage delivery safeguards', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('leaves the detail page after receipt and ignores an accidental second click', async () => {
    let completeReceipt;
    chequeRequestApi.confirmBranchReceipt.mockImplementation(() => new Promise((resolve) => {
      completeReceipt = resolve;
    }));
    renderPage(REQUEST_STATUS.DISPATCHED);
    const receiptButton = await screen.findByRole('button', { name: /confirm branch receipt/i });

    fireEvent.click(receiptButton);
    fireEvent.click(receiptButton);
    expect(chequeRequestApi.confirmBranchReceipt).toHaveBeenCalledOnce();
    completeReceipt({});

    expect(await screen.findByText('Request list')).toBeInTheDocument();
  });

  it('requires all three confirmations and submits the request version', async () => {
    chequeRequestApi.markDelivered.mockResolvedValue({});
    renderPage(REQUEST_STATUS.RECEIVED_BY_BRANCH);

    fireEvent.click(await screen.findByRole('button', { name: /mark as delivered/i }));
    expect(chequeRequestApi.markDelivered).not.toHaveBeenCalled();
    const confirmButton = screen.getByRole('button', { name: /confirm delivery/i });
    expect(confirmButton).toBeDisabled();

    fireEvent.click(screen.getByLabelText('Customer identity has been verified'));
    fireEvent.click(screen.getByLabelText('Cheque book has been handed to the customer'));
    fireEvent.change(screen.getByLabelText(/last 4 digits/i), { target: { value: '48ab21' } });
    expect(screen.getByLabelText(/last 4 digits/i)).toHaveValue('4821');
    expect(confirmButton).toBeEnabled();
    fireEvent.click(confirmButton);

    await waitFor(() => expect(chequeRequestApi.markDelivered).toHaveBeenCalledWith(
      'CBR-2026-000118',
      {
        accountLastFour: '4821',
        identityVerified: true,
        handoverConfirmed: true,
        requestVersion: 6,
      },
    ));
    expect(await screen.findByText('Request list')).toBeInTheDocument();
  });

  it('keeps the modal open, clears the digits, and shows a safe mismatch message', async () => {
    chequeRequestApi.markDelivered.mockRejectedValue(
      Object.assign(new Error('The delivery details could not be verified.'), {
        code: 'ACCOUNT_SUFFIX_MISMATCH',
      }),
    );
    renderPage(REQUEST_STATUS.RECEIVED_BY_BRANCH);

    fireEvent.click(await screen.findByRole('button', { name: /mark as delivered/i }));
    fireEvent.click(screen.getByLabelText('Customer identity has been verified'));
    fireEvent.click(screen.getByLabelText('Cheque book has been handed to the customer'));
    const accountInput = screen.getByLabelText(/last 4 digits/i);
    fireEvent.change(accountInput, { target: { value: '9999' } });
    fireEvent.click(screen.getByRole('button', { name: /confirm delivery/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/do not match/i);
    expect(screen.getByRole('dialog', { name: 'Confirm customer collection' })).toBeInTheDocument();
    expect(accountInput).toHaveValue('');
    await waitFor(() => expect(accountInput).toHaveFocus());
  });

  it('checks the latest status after a network failure and treats an applied delivery as success', async () => {
    chequeRequestApi.markDelivered.mockRejectedValue(
      Object.assign(new Error('The portal service could not be reached.'), {
        code: 'NETWORK_ERROR',
        retryable: true,
      }),
    );
    renderPage(REQUEST_STATUS.RECEIVED_BY_BRANCH);
    await screen.findByRole('button', { name: /mark as delivered/i });
    chequeRequestApi.getRequestById.mockResolvedValueOnce({
      request: request(REQUEST_STATUS.DELIVERED),
    });

    fireEvent.click(screen.getByRole('button', { name: /mark as delivered/i }));
    fireEvent.click(screen.getByLabelText('Customer identity has been verified'));
    fireEvent.click(screen.getByLabelText('Cheque book has been handed to the customer'));
    fireEvent.change(screen.getByLabelText(/last 4 digits/i), { target: { value: '4821' } });
    fireEvent.click(screen.getByRole('button', { name: /confirm delivery/i }));

    await waitFor(() => expect(chequeRequestApi.getRequestById).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Request list')).toBeInTheDocument();
  });
});
