import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../services/api/customerApi', () => ({
  customerApi: { getCustomerByAccount: vi.fn(), getManagersChequeAccount: vi.fn() },
}));

vi.mock('../../services/api/chequeRequestApi', () => ({
  chequeRequestApi: { createRequest: vi.fn() },
}));

vi.mock('../../hooks/useNotificationSummary', () => ({
  requestNotificationRefresh: vi.fn(),
}));

vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

import { chequeRequestApi } from '../../services/api/chequeRequestApi';
import { customerApi } from '../../services/api/customerApi';
import { NewRequestPage } from './NewRequestPage';

const eligibleResult = (accountNumber) => ({
  customer: {
    name: 'Ada Customer',
    accountNumber,
    acctOwnership: 'CURRENT INDIVIDUAL',
    accountHomeBranchName: 'Victoria Island',
    accountHomeBranchCode: '001',
    servicingBranchName: 'Victoria Island',
    servicingBranchCode: '001',
    kycLevel: '3',
    restrictions: 'NONE',
  },
  eligibility: { eligible: true, checks: [['Active account', true]] },
});

const renderPage = () => render(
  <MemoryRouter>
    <NewRequestPage />
  </MemoryRouter>,
);

async function search(accountNumber, chequeType = '01') {
  fireEvent.change(screen.getByLabelText(/cheque type/i), { target: { value: chequeType } });
  fireEvent.change(screen.getByLabelText(/account number/i), {
    target: { value: accountNumber },
  });
  fireEvent.click(screen.getByRole('button', { name: /^search$/i }));
  await screen.findByLabelText('50 leaves');
}

describe('NewRequestPage cheque type', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    customerApi.getCustomerByAccount.mockImplementation(async (accountNumber) => eligibleResult(accountNumber));
    customerApi.getManagersChequeAccount.mockResolvedValue(eligibleResult('0123456789'));
  });

  it('submits the selected cheque type code', async () => {
    chequeRequestApi.createRequest.mockResolvedValue({
      request: {
        id: 'CBR-001',
        leafCount: 50,
        chequeTypeCode: '02',
        chequeTypeLabel: 'Corporate Account',
      },
    });
    renderPage();
    await search('0123456789', '02');

    fireEvent.click(screen.getByLabelText('50 leaves'));
    fireEvent.click(screen.getByRole('button', { name: /review request/i }));

    expect(screen.getByRole('dialog', { name: /confirm cheque book request/i }))
      .toHaveTextContent('02 — Corporate Account');
    fireEvent.click(screen.getByRole('button', { name: /^submit request$/i }));

    await waitFor(() => expect(chequeRequestApi.createRequest).toHaveBeenCalledWith({
      accountNumber: '0123456789',
      leafCount: 50,
      chequeType: '02',
    }));
    expect(await screen.findByText('02 — Corporate Account')).toBeInTheDocument();
    expect(customerApi.getCustomerByAccount).toHaveBeenCalledWith('0123456789', '02');
  });

  it('clears an existing lookup when the cheque type changes', async () => {
    renderPage();
    await search('0123456789');
    expect(screen.getByText('Customer information')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/cheque type/i), { target: { value: '03' } });

    expect(screen.queryByText('Customer information')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/account number/i)).not.toBeInTheDocument();
    expect(await screen.findByText(/manager's cheque account information/i)).toBeInTheDocument();
  });

  it("loads and submits the branch's configured Manager's Cheque account automatically", async () => {
    chequeRequestApi.createRequest.mockResolvedValue({
      request: {
        id: 'CBR-003',
        leafCount: 50,
        chequeTypeCode: '03',
        chequeTypeLabel: "Manager's Cheque",
      },
    });
    renderPage();
    fireEvent.change(screen.getByLabelText(/cheque type/i), { target: { value: '03' } });

    expect(await screen.findByText(/no customer debit will be performed after bom approval/i))
      .toBeInTheDocument();
    expect(customerApi.getManagersChequeAccount).toHaveBeenCalledOnce();
    expect(customerApi.getCustomerByAccount).not.toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText('50 leaves'));
    fireEvent.click(screen.getByRole('button', { name: /review request/i }));
    fireEvent.click(screen.getByRole('button', { name: /^submit request$/i }));

    await waitFor(() => expect(chequeRequestApi.createRequest).toHaveBeenCalledWith({
      leafCount: 50,
      chequeType: '03',
    }));
  });
});
