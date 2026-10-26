import { apiRequest } from './client';

export const customerApi = {
  getCustomerByAccount: (accountNumber) =>
    apiRequest(`/accounts/${encodeURIComponent(accountNumber)}/eligibility`),
};
