export const APPROVAL_MODES = {
  AUTO: 'AUTO',
  MAKER_CHECKER: 'MAKER_CHECKER',
};

// Confirmed frontend choices; keep centralized until supplied by backend configuration.
export const CHEQUE_LEAF_OPTIONS = [50, 100];

export const CHEQUE_TYPE_OPTIONS = [
  { code: '01', label: 'Individual Current Account' },
  { code: '02', label: 'Corporate Account' },
  { code: '03', label: "Managers Cheque" },
];

export function formatChequeType(code, label) {
  if (!code) return 'Not specified';
  const resolvedLabel = label || CHEQUE_TYPE_OPTIONS.find((type) => type.code === code)?.label;
  return resolvedLabel ? `${code} — ${resolvedLabel}` : code;
}

// Provisional. Replace with a backend-provided policy value once confirmed.
export const workflowConfig = {
  approvalMode: APPROVAL_MODES.MAKER_CHECKER,
  vendorProcessingIsExternal: true,
  chequeMaintenanceIsExternal: true,
};
