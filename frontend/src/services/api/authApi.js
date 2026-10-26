import {
  apiRequest,
  clearAuthenticationSession,
  currentRefreshToken,
  establishAuthenticationSession,
  refreshAuthenticationSession,
} from './client';

const data = (response) => response?.data;

export const authApi = {
  async login(credentials) {
    const response = await apiRequest('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    });

    if (response?.code === 'MFA_REQUIRED') {
      return { ...data(response), mfaRequired: true };
    }

    const session = data(response);
    establishAuthenticationSession(session);
    return session;
  },
  async verifyEntrust(challengeId, token) {
    const session = data(await apiRequest('/auth/mfa/entrust/verify', {
      method: 'POST',
      body: JSON.stringify({ challengeId, token: token.trim() }),
    }));
    establishAuthenticationSession(session);
    return session;
  },
  async profile() {
    return data(await apiRequest('/auth/profile'));
  },
  async refresh() {
    return refreshAuthenticationSession();
  },
  async logout() {
    const refreshToken = currentRefreshToken();
    try {
      await apiRequest('/auth/logout', {
        method: 'POST',
        headers: refreshToken ? { Authorization: `Bearer ${refreshToken}` } : {},
      });
    } finally {
      clearAuthenticationSession();
    }
  },
  clearSession() {
    clearAuthenticationSession();
  },
};
