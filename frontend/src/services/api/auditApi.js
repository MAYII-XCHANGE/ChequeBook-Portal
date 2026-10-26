import { apiRequest, queryString } from './client';

export const auditApi = {
  getAuditEvents: (filters = {}) =>
    apiRequest(`/audit-events${queryString(filters)}`),
};
