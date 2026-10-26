import axios from 'axios';

const API_URL = 'http://localhost:8083/api/v1';

export const apiError = (code, message, retryable = false, fieldErrors = {}) =>
  Object.assign(new Error(message), { code, retryable, fieldErrors });

export const httpClient = axios.create({
  baseURL: API_URL,
  headers: { Accept: 'application/json' },
  // Keep HTTP errors as responses so backend error details remain available.
  validateStatus: () => true,
});

let refreshPromise;
let authenticationEpoch = 0;
const ACCESS_TOKEN_KEY = 'portal.accessToken';
const REFRESH_TOKEN_KEY = 'portal.refreshToken';

const storage = () => (typeof window === 'undefined' ? null : window.sessionStorage);
const storedToken = (key) => storage()?.getItem(key) || null;

const persistTokens = ({ accessToken, refreshToken } = {}) => {
  if (!accessToken || !refreshToken) {
    throw apiError('INVALID_AUTH_RESPONSE', 'The authentication service did not return both bearer tokens.');
  }
  storage()?.setItem(ACCESS_TOKEN_KEY, accessToken);
  storage()?.setItem(REFRESH_TOKEN_KEY, refreshToken);
};

export const establishAuthenticationSession = (session) => {
  persistTokens(session);
  authenticationEpoch += 1;
};

export const clearAuthenticationSession = () => {
  storage()?.removeItem(ACCESS_TOKEN_KEY);
  storage()?.removeItem(REFRESH_TOKEN_KEY);
  authenticationEpoch += 1;
};

export const currentRefreshToken = () => storedToken(REFRESH_TOKEN_KEY);

const isSuccessful = (status) => status >= 200 && status < 300;
const authenticationPath = (path) => path.split('?')[0];
const canRefresh = (path) => ![
  '/auth/login',
  '/auth/mfa/entrust/verify',
  '/auth/refresh-token',
  '/auth/logout',
]
  .includes(authenticationPath(path));

const networkError = () =>
  apiError('NETWORK_ERROR', 'The portal service could not be reached. Check your connection and retry.', true);

const requestConfig = (path, options = {}) => {
  const { body, ...config } = options;
  const headers = { ...config.headers };
  const accessToken = storedToken(ACCESS_TOKEN_KEY);
  if (accessToken && !headers.Authorization) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;

  if (body !== undefined && !isFormData && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  return { ...config, url: path, data: body, headers };
};

const send = async (path, options = {}) => {
  try {
    return await httpClient.request(requestConfig(path, options));
  } catch {
    throw networkError();
  }
};

const refreshAuthentication = () => {
  if (!refreshPromise) {
    const refreshToken = currentRefreshToken();
    if (!refreshToken) {
      return Promise.reject(Object.assign(
        apiError('UNAUTHENTICATED', 'Your session has expired. Please sign in again.'),
        { authenticationRejected: true },
      ));
    }
    refreshPromise = send('/auth/refresh-token', {
      method: 'POST',
      headers: { Authorization: `Bearer ${refreshToken}` },
    })
      .then((response) => {
        if (isSuccessful(response.status)) {
          persistTokens(response.data?.data);
          return response.data?.data;
        }

        const payload = response.data && typeof response.data === 'object' ? response.data : {};
        if ([401, 403].includes(response.status)) {
          throw Object.assign(
            apiError('UNAUTHENTICATED', 'Your session has expired. Please sign in again.'),
            { authenticationRejected: true },
          );
        }

        throw apiError(
          payload.code || `HTTP_${response.status}`,
          payload.message || 'The authentication service could not refresh your session.',
          Boolean(payload.retryable),
          payload.fieldErrors || {},
        );
      })
      .finally(() => { refreshPromise = undefined; });
  }

  return refreshPromise;
};

export const refreshAuthenticationSession = () => refreshAuthentication();

async function requestWithAuthentication(path, options = {}, retried = false) {
  const requestAuthenticationEpoch = authenticationEpoch;
  const response = await send(path, options);

  if (response.status !== 401 || !canRefresh(path)) return response;

  if (retried) {
    if (requestAuthenticationEpoch === authenticationEpoch) {
      window.dispatchEvent(new Event('portal:unauthorized'));
    }
    return response;
  }

  try {
    await refreshAuthentication();
    return requestWithAuthentication(path, options, true);
  } catch (error) {
    if (!error.authenticationRejected) throw error;
    if (requestAuthenticationEpoch === authenticationEpoch) {
      window.dispatchEvent(new Event('portal:unauthorized'));
    }
    return response;
  }
}

const errorFromResponse = (response, fallback) => {
  const payload = response.data && typeof response.data === 'object' ? response.data : {};
  return apiError(
    payload.code || `HTTP_${response.status}`,
    payload.message || fallback,
    Boolean(payload.retryable),
    payload.fieldErrors || {},
  );
};

export async function apiRequest(path, options = {}) {
  const response = await requestWithAuthentication(path, options);
  if (!isSuccessful(response.status)) {
    throw errorFromResponse(response, 'The request could not be completed.');
  }
  return response.status === 204 ? undefined : response.data;
}

const isBlob = (value) => typeof Blob !== 'undefined' && value instanceof Blob;

const downloadErrorPayload = async (response) => {
  if (response.data && typeof response.data === 'object' && !isBlob(response.data)) {
    return response.data;
  }
  if (!isBlob(response.data)) return {};

  try {
    return JSON.parse(await response.data.text());
  } catch {
    return {};
  }
};

export async function apiDownload(path) {
  const response = await requestWithAuthentication(path, { responseType: 'blob' });
  if (!isSuccessful(response.status)) {
    const payload = await downloadErrorPayload(response);
    throw apiError(
      payload.code || `HTTP_${response.status}`,
      payload.message || 'The file could not be downloaded.',
      Boolean(payload.retryable),
      payload.fieldErrors || {},
    );
  }

  const disposition = response.headers?.get?.('content-disposition')
    || response.headers?.['content-disposition']
    || '';
  const filename = disposition.match(/filename="?([^";]+)"?/)?.[1] || 'download';
  const url = URL.createObjectURL(response.data);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  return filename;
}

export const queryString = (params = {}) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null) query.set(key, value);
  });
  return query.toString() ? `?${query}` : '';
};
