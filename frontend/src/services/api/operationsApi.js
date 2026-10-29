import { REQUEST_STATUS } from '../../constants/status';
import { apiError, apiRequest } from './client';

export const approvalApi = {
  getPending: () => apiRequest('/approvals/pending'),
  getFailedDebits: () => apiRequest('/approvals/debit-failures'),
  approveRequest: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/approve`, {
      method: 'POST',
    }),
  rejectRequest: (id, { reason }) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
  retryDebit: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/debit/retry`, {
      method: 'POST',
    }),
};

const paths = {
  [REQUEST_STATUS.PRINTED]: 'dispatch',
  [REQUEST_STATUS.FINACLE_REGISTERED]: 'dispatch',
};

export const processingApi = {
  getQueue: () => apiRequest('/processing/queue'),
  retryReversal: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/reversal/retry`, {
      method: 'POST',
    }),
  reconcileReversal: (id, body) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/reversal/reconcile`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  async advance(id, currentStatus) {
    const path = paths[currentStatus];
    if (!path)
      throw apiError('INVALID_STATUS_TRANSITION', 'This request has no manual transition.');
    return apiRequest(`/cheque-requests/${encodeURIComponent(id)}/${path}`, {
      method: 'POST',
    });
  },
};
