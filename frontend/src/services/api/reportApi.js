import { apiDownload, apiRequest, queryString } from './client';

export const reportApi = {
  getRequestReport: (filters = {}) =>
    apiRequest(`/reports/requests${queryString(filters)}`),
  downloadRequestReport: (filters = {}) =>
    apiDownload(`/reports/requests/export${queryString(filters)}`),
};
