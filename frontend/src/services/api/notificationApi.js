import { apiRequest } from './client';

export const notificationApi = {
  summary: () => apiRequest('/notifications/summary'),
};
