export const NOTIFICATION_ACTION_CODES = {
  APPROVAL_REQUIRED: "APPROVAL_REQUIRED",
  DEBIT_RETRY_REQUIRED: "DEBIT_RETRY_REQUIRED",
  VENDOR_EXPORT_REQUIRED: "VENDOR_EXPORT_REQUIRED",
  VENDOR_SEND_REQUIRED: "VENDOR_SEND_REQUIRED",
  VENDOR_PRINT_REQUIRED: "VENDOR_PRINT_REQUIRED",
  LIFECYCLE_UPDATE_REQUIRED: "LIFECYCLE_UPDATE_REQUIRED",
  REVERSAL_REVIEW_REQUIRED: "REVERSAL_REVIEW_REQUIRED",
  BRANCH_RECEIPT_REQUIRED: "BRANCH_RECEIPT_REQUIRED",
  DELIVERY_CONFIRMATION_REQUIRED: "DELIVERY_CONFIRMATION_REQUIRED",
};

export const NOTIFICATION_ACTIONS = {
  [NOTIFICATION_ACTION_CODES.APPROVAL_REQUIRED]: {
    label: "Pending approvals",
    path: "/approvals",
  },
  [NOTIFICATION_ACTION_CODES.DEBIT_RETRY_REQUIRED]: {
    label: "Failed debits awaiting retry",
    path: "/approvals",
  },
  [NOTIFICATION_ACTION_CODES.VENDOR_EXPORT_REQUIRED]: {
    label: "Ready for vendor export",
    path: "/vendor-exports",
  },
  [NOTIFICATION_ACTION_CODES.VENDOR_SEND_REQUIRED]: {
    label: "Vendor send confirmation required",
    path: "/vendor-exports",
  },
  [NOTIFICATION_ACTION_CODES.VENDOR_PRINT_REQUIRED]: {
    label: "Vendor printing confirmation required",
    path: "/vendor-exports",
  },
  [NOTIFICATION_ACTION_CODES.LIFECYCLE_UPDATE_REQUIRED]: {
    label: "Lifecycle update required",
    path: "/processing",
  },
  [NOTIFICATION_ACTION_CODES.REVERSAL_REVIEW_REQUIRED]: {
    label: "Charge reversal review required",
    path: "/processing",
  },
  [NOTIFICATION_ACTION_CODES.BRANCH_RECEIPT_REQUIRED]: {
    label: "Branch receipt confirmation required",
    path: "/cheque-requests",
  },
  [NOTIFICATION_ACTION_CODES.DELIVERY_CONFIRMATION_REQUIRED]: {
    label: "Customer delivery confirmation required",
    path: "/cheque-requests",
  },
};

export function notificationCountForPath(actions, path) {
  return actions.reduce((total, action) => {
    const definition = NOTIFICATION_ACTIONS[action.code];
    return definition?.path === path ? total + action.count : total;
  }, 0);
}

export function formatNotificationCount(count) {
  return count > 99 ? "99+" : String(count);
}
