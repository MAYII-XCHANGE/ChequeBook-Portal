import { describe, expect, it } from 'vitest';
import { formatDate } from '../../utils/formatters';
import { buildRequestReportCsv } from './requestReportCsv';

describe('request report CSV', () => {
  it('exports the table columns and displayed values safely', () => {
    const createdAt = '2026-09-22T12:00:00Z';
    const csv = buildRequestReportCsv([{
      id: 'CBR-2026-000028',
      customer: '=HYPERLINK("https://example.test","Open")',
      accountNumber: '1001004215',
      acctOwnership: 'SINGLE',
      chequeTypeCode: '02',
      chequeTypeLabel: 'Corporate Account',
      branch: 'Victoria Island, Lagos',
      servicingBranch: 'Victoria Island, Lagos',
      servicingBranchCode: '001',
      accountHomeBranch: 'Ikeja - 03',
      accountHomeBranchCode: '003',
      createdBy: 'CSO User',
      leafCount: 50,
      bookletCount: 1,
      createdAt,
      status: 'APPROVED',
      debitStatus: 'SUCCESSFUL',
      chargeAmount: '1000.00',
      vatAmount: '75.00',
      totalDebit: '1075.00',
      currency: 'NGN',
    }]);

    expect(csv.startsWith('Request ID,Customer Name,Account Number,Account Ownership,Cheque Type,Starting Cheque Number,Ending Cheque Number,Vendor Reference,Finacle Registration Status,Domicile Branch,Domicile Branch Code')).toBe(true);
    expect(csv).toContain('"02 — Corporate Account"');
    expect(csv).toContain('"1001004215"');
    expect(csv).toContain(`"${formatDate(createdAt)}"`);
    expect(csv).toContain('"Ready for export"');
    expect(csv).toContain('"Victoria Island, Lagos"');
    expect(csv).toContain('"Ikeja - 03"');
    expect(csv).toContain('"\'=HYPERLINK(""https://example.test"",""Open"")"');
    expect(csv).not.toContain('Action');
    expect(csv).toContain('"CSO User"');
    expect(csv).toContain('"1075.00"');
  });
});
