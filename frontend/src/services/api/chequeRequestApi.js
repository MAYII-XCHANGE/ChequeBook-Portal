import { apiRequest, queryString } from './client';

export const chequeRequestApi = {
  getRequests: (filters = {}) =>
    apiRequest(`/cheque-requests${queryString(filters)}`),
  getRequestById: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}`),
  createRequest: ({ accountNumber, leafCount, chequeType }) =>
    apiRequest('/cheque-requests', {
      method: 'POST',
      body: JSON.stringify({ accountNumber, leafCount, chequeType }),
    }),
  confirmBranchReceipt: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/receive-at-branch`, {
      method: 'POST',
    }),
  markDelivered: (id, confirmation) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}/deliver`, {
      method: 'POST',
      body: JSON.stringify(confirmation),
    }),
};
