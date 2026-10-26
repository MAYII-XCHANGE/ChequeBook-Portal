import { STATUS_CONFIG } from '../../constants/status';
import { formatChequeType } from '../../constants/workflow';
import { formatDate } from '../../utils/formatters';

const headers = [
  'Request ID', 'Customer Name', 'Account Number', 'Account Ownership',
  'Cheque Type',
  'Starting Cheque Number', 'Ending Cheque Number', 'Vendor Reference', 'Finacle Registration Status',
  'Domicile Branch', 'Domicile Branch Code', 'Initiating Branch', 'Initiating Branch Code',
  'Initiated By', 'Leaf Count', 'Booklet Count', 'Created Date', 'Status', 'Debit Status',
  'Charge Amount', 'VAT Amount', 'Total Debit', 'Currency',
];

const csvCell = (value) => {
  const text = String(value ?? '');
  // Prevent spreadsheet applications from evaluating customer-supplied text as a formula.
  const safe = /^\s*[=+\-@]/.test(text) ? `'${text}` : text;
  return `"${safe.replaceAll('"', '""')}"`;
};

export function buildRequestReportCsv(rows) {
  const lines = [headers.join(',')];
  for (const row of rows) {
    lines.push([
      row.id,
      row.customer,
      row.accountNumber,
      row.acctOwnership,
      row.chequeType || formatChequeType(row.chequeTypeCode, row.chequeTypeLabel),
      row.startChequeNumber,
      row.endChequeNumber,
      row.vendorReference,
      row.finacleRegistrationStatus,
      row.accountHomeBranch,
      row.accountHomeBranchCode,
      row.servicingBranch || row.branch,
      row.servicingBranchCode || row.branchCode,
      row.createdBy,
      row.leafCount,
      row.bookletCount,
      formatDate(row.createdAt),
      STATUS_CONFIG[row.status]?.label || row.status || 'Unknown',
      row.debitStatus,
      row.chargeAmount,
      row.vatAmount,
      row.totalDebit,
      row.currency,
    ].map(csvCell).join(','));
  }
  return `${lines.join('\r\n')}\r\n`;
}

export function downloadRequestReportCsv(rows, filters) {
  const parts = ['cheque-request-report', filters.from, filters.to];
  if (filters.branch) parts.push(`branch-${filters.branch}`);
  const filename = `${parts.filter(Boolean).join('_')}.csv`;
  const blob = new Blob(['\uFEFF', buildRequestReportCsv(rows)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  return filename;
}
