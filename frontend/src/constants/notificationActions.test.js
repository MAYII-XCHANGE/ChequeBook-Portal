import { describe, expect, it } from 'vitest';
import {
  formatNotificationCount,
  NOTIFICATION_ACTION_CODES,
  notificationCountForPath,
} from './notificationActions';

describe('notification action mapping', () => {
  it('aggregates every action category mapped to the same application screen', () => {
    const actions = [
      { code: NOTIFICATION_ACTION_CODES.APPROVAL_REQUIRED, count: 1 },
      { code: NOTIFICATION_ACTION_CODES.DEBIT_RETRY_REQUIRED, count: 4 },
      { code: NOTIFICATION_ACTION_CODES.VENDOR_EXPORT_REQUIRED, count: 2 },
      { code: NOTIFICATION_ACTION_CODES.VENDOR_SEND_REQUIRED, count: 3 },
      { code: NOTIFICATION_ACTION_CODES.VENDOR_PRINT_REQUIRED, count: 4 },
      { code: NOTIFICATION_ACTION_CODES.LIFECYCLE_UPDATE_REQUIRED, count: 5 },
      { code: NOTIFICATION_ACTION_CODES.REVERSAL_REVIEW_REQUIRED, count: 2 },
      { code: NOTIFICATION_ACTION_CODES.BRANCH_RECEIPT_REQUIRED, count: 2 },
      { code: NOTIFICATION_ACTION_CODES.DELIVERY_CONFIRMATION_REQUIRED, count: 6 },
    ];

    expect(notificationCountForPath(actions, '/vendor-exports')).toBe(9);
    expect(notificationCountForPath(actions, '/approvals')).toBe(5);
    expect(notificationCountForPath(actions, '/processing')).toBe(7);
    expect(notificationCountForPath(actions, '/cheque-requests')).toBe(8);
  });

  it('caps large visual badge values without changing the underlying count', () => {
    expect(formatNotificationCount(12)).toBe('12');
    expect(formatNotificationCount(100)).toBe('99+');
  });
});
