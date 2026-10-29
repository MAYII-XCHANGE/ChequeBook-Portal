import { apiRequest } from './client';

export const customerApi = {
  getManagersChequeAccount: () => apiRequest('/accounts/managers-cheque'),
  getCustomerByAccount: (accountNumber, chequeType) => {
    const typeQuery = chequeType ? `?chequeType=${encodeURIComponent(chequeType)}` : '';
    return apiRequest(`/accounts/${encodeURIComponent(accountNumber)}/eligibility${typeQuery}`);
  },
};
