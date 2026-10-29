import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../services/api/exportApi', () => ({
  exportApi: {
    eligible: vi.fn(), list: vi.fn(), capabilities: vi.fn(), create: vi.fn(), download: vi.fn(),
    confirmSent: vi.fn(), confirmPrinted: vi.fn(),
    importPrintResponse: vi.fn(), importPrintResponseWorkbook: vi.fn(), printResponse: vi.fn(), retryRegistration: vi.fn(),
    reconcileRegistration: vi.fn(),
  },
}));

vi.mock('../../hooks/useNotificationSummary', () => ({ requestNotificationRefresh: vi.fn() }));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

import { requestNotificationRefresh } from '../../hooks/useNotificationSummary';
import { exportApi } from '../../services/api/exportApi';
import { VendorExportsPage } from './VendorExportsPage';

const batches = [
  { batchNumber: 'VEX-EXPORTED', recordCount: 2, createdAt: '2026-10-15T08:00:00Z', status: 'EXPORTED' },
  { batchNumber: 'VEX-SENT', recordCount: 1, createdAt: '2026-10-15T09:00:00Z', status: 'SENT' },
  { batchNumber: 'VEX-PRINTED', recordCount: 1, createdAt: '2026-10-15T10:00:00Z', status: 'PRINTED' },
];

const csv = [
  'S/N,Account Number,Account Name,Branch Name,Branch Address,Sort Code,Cheque Type,No of Leaves,No of Booklets,Delivery SolID,Delivery Address,Scheme Code,Currency,Range Start,Range Stop',
  '1,0123456789,Customer,Victoria Island,1 Bank Street,001,01,50,1,001,Victoria Island,CUR,NGN,001001,001050',
].join('\n');

const renderPage = () => render(<VendorExportsPage />);

describe('VendorExportsPage print-response workflow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    exportApi.eligible.mockResolvedValue({ data: [] });
    exportApi.list.mockResolvedValue(batches);
    exportApi.capabilities.mockResolvedValue({ finacleRegistrationEnabled: true });
    exportApi.printResponse.mockResolvedValue({ data: [] });
  });

  it('offers vendor-response upload only for a sent batch', async () => {
    renderPage();

    expect(await screen.findAllByRole('button', { name: 'Upload vendor response' })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Confirm printed' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View serial allocations' })).toBeInTheDocument();
  });

  it('confirms the complete batch in manual printing mode', async () => {
    exportApi.capabilities.mockResolvedValue({ finacleRegistrationEnabled: false });
    exportApi.confirmPrinted.mockResolvedValue({ batchNumber: 'VEX-SENT', status: 'PRINTED' });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm printed' }));
    expect(screen.getByRole('dialog', { name: 'Confirm vendor printing' })).toHaveTextContent('VEX-SENT');
    expect(screen.getByRole('dialog', { name: 'Confirm vendor printing' })).toHaveTextContent(
      'All 1 request in this batch will be marked as printed.',
    );
    fireEvent.click(within(screen.getByRole('dialog', { name: 'Confirm vendor printing' }))
      .getByRole('button', { name: 'Confirm printed' }));

    await waitFor(() => expect(exportApi.confirmPrinted).toHaveBeenCalledWith('VEX-SENT'));
    expect(requestNotificationRefresh).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Upload vendor response' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'View serial allocations' })).not.toBeInTheDocument();
  });

  it('opens and cancels the upload without submitting', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Upload vendor response' }));

    expect(screen.getByRole('dialog', { name: 'Upload vendor print response' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('dialog', { name: 'Upload vendor print response' })).not.toBeInTheDocument();
    expect(exportApi.importPrintResponse).not.toHaveBeenCalled();
  });

  it('parses the CSV and submits the complete vendor response', async () => {
    exportApi.importPrintResponse.mockResolvedValue({ importedRows: 1, registrations: [] });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Upload vendor response' }));
    fireEvent.change(screen.getByLabelText('Vendor response file'), {
      target: { files: [{ name: 'vendor-response.csv', text: vi.fn().mockResolvedValue(csv) }] },
    });

    expect(await screen.findByText('1 vendor response row(s) ready to import.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Import and register in Finacle' }));

    await waitFor(() => expect(exportApi.importPrintResponse).toHaveBeenCalledWith('VEX-SENT', [{
      serialNumber: '1', accountNumber: '0123456789', accountName: 'Customer',
      branchName: 'Victoria Island', branchAddress: '1 Bank Street', sortCode: '001', chequeType: '01',
      numberOfLeaves: 50, numberOfBooklets: 1, deliverySolId: '001',
      deliveryAddress: 'Victoria Island', schemeCode: 'CUR', currency: 'NGN',
      rangeStart: '001001', rangeStop: '001050',
    }]));
    expect(requestNotificationRefresh).toHaveBeenCalledOnce();
  });

  it('submits an Excel response as a workbook upload', async () => {
    exportApi.importPrintResponseWorkbook.mockResolvedValue({ importedRows: 1, registrations: [] });
    const workbook = new File(['xlsx-content'], 'vendor-response.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Upload vendor response' }));
    fireEvent.change(screen.getByLabelText('Vendor response file'), { target: { files: [workbook] } });

    expect(await screen.findByText('vendor-response.xlsx is ready to import.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Import and register in Finacle' }));

    await waitFor(() => expect(exportApi.importPrintResponseWorkbook)
      .toHaveBeenCalledWith('VEX-SENT', workbook));
    expect(exportApi.importPrintResponse).not.toHaveBeenCalled();
  });

  it('keeps validation errors visible in the upload modal', async () => {
    exportApi.importPrintResponse.mockRejectedValue(new Error('Row 2: serial range does not equal the leaf count.'));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Upload vendor response' }));
    fireEvent.change(screen.getByLabelText('Vendor response file'), {
      target: { files: [{ name: 'vendor-response.csv', text: vi.fn().mockResolvedValue(csv) }] },
    });
    await screen.findByText('1 vendor response row(s) ready to import.');
    fireEvent.click(screen.getByRole('button', { name: 'Import and register in Finacle' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/serial range does not equal/i);
    expect(screen.getByRole('dialog', { name: 'Upload vendor print response' })).toBeInTheDocument();
  });

  it('rejects a CSV that does not contain every request in the batch', async () => {
    const incompleteBatchCsv = `${csv}\n2,0123456790,Second Customer,Victoria Island,1 Bank Street,001,01,50,1,001,Victoria Island,CUR,NGN,001051,001100`;
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Upload vendor response' }));
    fireEvent.change(screen.getByLabelText('Vendor response file'), {
      target: { files: [{ name: 'vendor-response.csv', text: vi.fn().mockResolvedValue(incompleteBatchCsv) }] },
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This batch expects 1 response row(s), but the file contains 2.',
    );
    expect(screen.getByRole('button', { name: 'Import and register in Finacle' })).toBeDisabled();
  });
});
