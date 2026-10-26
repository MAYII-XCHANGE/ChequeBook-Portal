import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../services/api/customerApi', () => ({
  customerApi: { getCustomerByAccount: vi.fn() },
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

async function search(accountNumber) {
  fireEvent.change(screen.getByLabelText(/customer account number/i), {
    target: { value: accountNumber },
  });
  fireEvent.click(screen.getByRole('button', { name: /^search$/i }));
  await screen.findByLabelText(/cheque type/i);
}

describe('NewRequestPage cheque type', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    customerApi.getCustomerByAccount.mockImplementation(async (accountNumber) => eligibleResult(accountNumber));
  });

  it('requires a cheque type and submits only its code', async () => {
    chequeRequestApi.createRequest.mockResolvedValue({
      request: {
        id: 'CBR-001',
        leafCount: 50,
        chequeTypeCode: '02',
        chequeTypeLabel: 'Corporate Account',
      },
    });
    renderPage();
    await search('0123456789');

    fireEvent.click(screen.getByLabelText('50 leaves'));
    expect(screen.getByRole('button', { name: /review request/i })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/cheque type/i), { target: { value: '02' } });
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
  });

  it('clears the selected cheque type when another account is searched', async () => {
    renderPage();
    await search('0123456789');
    fireEvent.change(screen.getByLabelText(/cheque type/i), { target: { value: '01' } });
    expect(screen.getByLabelText(/cheque type/i)).toHaveValue('01');

    await search('0123456790');

    expect(screen.getByLabelText(/cheque type/i)).toHaveValue('');
  });
});
