import { apiRequest, queryString } from './client';

export const chequeRequestApi = {
  getRequests: (filters = {}) =>
    apiRequest(`/cheque-requests${queryString(filters)}`),
  getRequestById: (id) =>
    apiRequest(`/cheque-requests/${encodeURIComponent(id)}`),
  createRequest: ({ accountNumber, leafCount, chequeType }) => {
    const body = { leafCount, chequeType };
    if (chequeType !== '03') body.accountNumber = accountNumber;
    return apiRequest('/cheque-requests', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  },
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
