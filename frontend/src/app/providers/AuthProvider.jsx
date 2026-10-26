import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../../services/api/authApi';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [pendingMfa, setPendingMfa] = useState(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let active = true;
    authApi.profile().then((current) => {
      if (!active) return;
      setUser(current);
    }).catch(() => {
      if (!active) return;
      setUser(null);
    }).finally(() => active && setInitializing(false));
    const unauthorized = () => {
      authApi.clearSession();
      setUser(null);
      setPendingMfa(null);
    };
    window.addEventListener('portal:unauthorized', unauthorized);
    return () => { active = false; window.removeEventListener('portal:unauthorized', unauthorized); };
  }, []);

  const value = useMemo(() => ({
    user,
    initializing,
    isAuthenticated: Boolean(user),
    pendingMfa,
    async login(credentials) {
      const result = await authApi.login(credentials);
      if (result?.mfaRequired) {
        setPendingMfa({
          challengeId: result.challengeId,
          expiresIn: result.expiresIn,
        });
        return result;
      }
      const current = await authApi.profile();
      setUser(current);
      return current;
    },
    async verifyEntrust(token) {
      if (!pendingMfa?.challengeId) {
        throw new Error('Your Entrust verification challenge is no longer available. Sign in again.');
      }
      await authApi.verifyEntrust(pendingMfa.challengeId, token);
      const current = await authApi.profile();
      setPendingMfa(null);
      setUser(current);
      return current;
    },
    cancelEntrust() {
      setPendingMfa(null);
    },
    async logout() {
      try { await authApi.logout(); } finally { setUser(null); }
    },
    clearAuthentication() {
      authApi.clearSession();
      setUser(null);
      setPendingMfa(null);
    },
  }), [user, pendingMfa, initializing]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
