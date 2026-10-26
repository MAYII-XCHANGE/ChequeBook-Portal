import { describe, expect, it } from 'vitest';
import { hasPermission, PERMISSIONS, ROLES } from './access';

describe('role permissions', () => {
  it('allows a branch CSO to create but not approve requests', () => {
    expect(hasPermission(ROLES.CSO, PERMISSIONS.CREATE_CHEQUE_REQUEST)).toBe(true);
    expect(hasPermission(ROLES.CSO, PERMISSIONS.VIEW_REPORTS)).toBe(true);
    expect(hasPermission(ROLES.CSO, PERMISSIONS.DELIVER_CHEQUE_REQUEST)).toBe(true);
    expect(hasPermission(ROLES.CSO, PERMISSIONS.APPROVE_CHEQUE_REQUEST)).toBe(false);
  });
  it('allows clearing operations to process and audit', () => {
    expect(hasPermission(ROLES.OPERATIONS, PERMISSIONS.PROCESS_CHEQUE_REQUEST)).toBe(true);
    expect(hasPermission(ROLES.OPERATIONS, PERMISSIONS.DELIVER_CHEQUE_REQUEST)).toBe(false);
    expect(hasPermission(ROLES.OPERATIONS, PERMISSIONS.VIEW_AUDIT_TRAIL)).toBe(true);
  });

  it('keeps the administrator restricted to configuration functions', () => {
    expect(hasPermission(ROLES.ADMIN, PERMISSIONS.USER_MANAGEMENT)).toBe(true);
    expect(hasPermission(ROLES.ADMIN, PERMISSIONS.VIEW_CHEQUE_REQUEST)).toBe(false);
  });
});
