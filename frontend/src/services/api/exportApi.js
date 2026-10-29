import { apiDownload, apiRequest } from './client';

const batchPath = (batch) => `/vendor-exports/${encodeURIComponent(batch)}`;

export const exportApi = {
  eligible: () => apiRequest('/vendor-exports/eligible-requests'),
  list: () => apiRequest('/vendor-exports'),
  capabilities: () => apiRequest('/vendor-exports/capabilities'),
  create: (requestIds) => apiRequest('/vendor-exports', {
    method: 'POST',
    body: JSON.stringify({ requestIds }),
  }),
  download: (batch) => apiDownload(`${batchPath(batch)}/download`),
  confirmSent: (batch) => apiRequest(`${batchPath(batch)}/confirm-sent`, { method: 'POST' }),
  confirmPrinted: (batch) => apiRequest(`${batchPath(batch)}/confirm-printed`, { method: 'POST' }),
  importPrintResponse: (batch, rows) => apiRequest(`${batchPath(batch)}/print-response`, {
    method: 'POST',
    body: JSON.stringify({ rows }),
  }),
  importPrintResponseWorkbook: (batch, file) => {
    const body = new FormData();
    body.append('file', file);
    return apiRequest(`${batchPath(batch)}/print-response-file`, { method: 'POST', body });
  },
  printResponse: (batch) => apiRequest(`${batchPath(batch)}/print-response`),
  retryRegistration: (requestId) => apiRequest(
    `/vendor-exports/registrations/${encodeURIComponent(requestId)}/retry`,
    { method: 'POST' },
  ),
  reconcileRegistration: (requestId, body) => apiRequest(
    `/vendor-exports/registrations/${encodeURIComponent(requestId)}/reconcile`,
    { method: 'POST', body: JSON.stringify(body) },
  ),
};
