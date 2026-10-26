import AxiosMockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { REQUEST_STATUS } from '../../constants/status';
import { authApi } from './authApi';
import { httpClient } from './client';
import { customerApi } from './customerApi';
import { chequeRequestApi } from './chequeRequestApi';
import { exportApi } from './exportApi';
import { processingApi } from './operationsApi';

describe('API contracts', () => {
  let mockApi;

  beforeEach(() => {
    window.sessionStorage.clear();
    mockApi = new AxiosMockAdapter(httpClient);
  });

  afterEach(() => {
    mockApi.restore();
    window.sessionStorage.clear();
  });

  it('returns backend-owned eligible customer details without cookie credentials', async () => {
    const payload = {
      customer: {
        name: 'Olumide Akinola',
        accountNumber: '0078901234',
        branchName: 'Victoria Island',
        branchCode: '02',
      },
      eligibility: {
        eligible: true,
        reason: null,
        checks: [['Core-banking name enquiry', true]],
      },
    };
    mockApi.onGet('/accounts/0078901234/eligibility').reply(200, payload);

    await expect(customerApi.getCustomerByAccount('0078901234')).resolves.toEqual(payload);
    expect(mockApi.history.get[0].url).toBe('/accounts/0078901234/eligibility');
    expect(mockApi.history.get[0].withCredentials).not.toBe(true);
  });

  it('preserves a backend ineligible result as a successful HTTP response', async () => {
    const payload = {
      customer: { accountNumber: '0098765432' },
      eligibility: { eligible: false, reason: 'Account is frozen.', checks: [] },
    };
    mockApi.onGet('/accounts/0098765432/eligibility').reply(200, payload);

    await expect(customerApi.getCustomerByAccount('0098765432')).resolves.toEqual(payload);
  });

  it('normalizes backend account errors', async () => {
    mockApi.onGet('/accounts/1111111111/eligibility').reply(
      404,
      { code: 'ACCOUNT_NOT_FOUND', message: 'No customer account was found.' },
    );

    await expect(customerApi.getCustomerByAccount('1111111111'))
      .rejects.toMatchObject({ code: 'ACCOUNT_NOT_FOUND' });
  });

  it('waits for Entrust verification before persisting authentication tokens', async () => {
    mockApi.onPost('/auth/login')
      .replyOnce(200, { code: 'MFA_REQUIRED', message: 'Entrust verification is required.', data: {
        challengeId: 'challenge-1', expiresIn: 300,
      } })
      .onPost('/auth/login')
      .replyOnce(401, { code: 'INVALID_CREDENTIALS', message: 'The username or password is incorrect.' });
    mockApi.onPost('/auth/mfa/entrust/verify').reply(200, {
      code: '200', message: 'Login successful.', data: {
        username: 'cso@example.bank', role: 'CSO', accessToken: 'access-1', refreshToken: 'refresh-1',
      },
    });

    await expect(authApi.login({ username: 'cso@example.bank', password: 'DirectoryPassword123!' }))
      .resolves.toEqual({ challengeId: 'challenge-1', expiresIn: 300, mfaRequired: true });
    expect(window.sessionStorage.getItem('portal.accessToken')).toBeNull();
    await expect(authApi.verifyEntrust('challenge-1', '640893'))
      .resolves.toMatchObject({ username: 'cso@example.bank', role: 'CSO' });
    expect(window.sessionStorage.getItem('portal.accessToken')).toBe('access-1');
    await expect(authApi.login({ username: 'cso@example.bank', password: 'wrong' }))
      .rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });

    expect(mockApi.history.post[0].url).toBe('/auth/login');
    expect(JSON.parse(mockApi.history.post[0].data)).toEqual({
      username: 'cso@example.bank',
      password: 'DirectoryPassword123!',
    });
    expect(JSON.parse(mockApi.history.post.find(({ url }) => url === '/auth/mfa/entrust/verify').data))
      .toEqual({ challengeId: 'challenge-1', token: '640893' });
  });

  it('refreshes once and retries a protected request after a 401 response', async () => {
    mockApi.onGet('/accounts/0078901234/eligibility')
      .replyOnce(401, { code: 'UNAUTHENTICATED' });
    mockApi.onGet('/accounts/0078901234/eligibility')
      .replyOnce(200, { eligibility: { eligible: true } });
    window.sessionStorage.setItem('portal.accessToken', 'expired-access');
    window.sessionStorage.setItem('portal.refreshToken', 'refresh-1');
    mockApi.onPost('/auth/refresh-token').reply(200, { code: '200', data: {
      accessToken: 'access-2', refreshToken: 'refresh-2',
    } });

    await expect(customerApi.getCustomerByAccount('0078901234'))
      .resolves.toEqual({ eligibility: { eligible: true } });
    expect(mockApi.history.post.filter(({ url }) => url === '/auth/refresh-token')).toHaveLength(1);
    expect(mockApi.history.get.filter(({ url }) => url.endsWith('/eligibility'))).toHaveLength(2);
    expect(mockApi.history.post.find(({ url }) => url === '/auth/refresh-token').headers.Authorization)
      .toBe('Bearer refresh-1');
    expect(mockApi.history.get[1].headers.Authorization).toBe('Bearer access-2');
  });

  it('does not expire the session for a temporary refresh network failure', async () => {
    const unauthorized = vi.fn();
    window.addEventListener('portal:unauthorized', unauthorized);
    window.sessionStorage.setItem('portal.accessToken', 'expired-access');
    window.sessionStorage.setItem('portal.refreshToken', 'refresh-1');
    mockApi.onGet('/accounts/0078901234/eligibility').reply(401);
    mockApi.onPost('/auth/refresh-token').networkError();

    await expect(customerApi.getCustomerByAccount('0078901234'))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR', retryable: true });
    expect(unauthorized).not.toHaveBeenCalled();
    window.removeEventListener('portal:unauthorized', unauthorized);
  });

  it('rejects attempts to skip required delivery lifecycle steps', async () => {
    await expect(processingApi.advance('CBR-2026-000118', REQUEST_STATUS.APPROVED))
      .rejects.toMatchObject({ code: 'INVALID_STATUS_TRANSITION' });
  });

  it('sends all delivery confirmations and the displayed request version', async () => {
    const confirmation = {
      accountLastFour: '4821',
      identityVerified: true,
      handoverConfirmed: true,
      requestVersion: 6,
    };
    mockApi.onPost('/cheque-requests/CBR-2026-000118/deliver').reply(200, {
      request: { id: 'CBR-2026-000118', status: REQUEST_STATUS.DELIVERED },
    });

    await chequeRequestApi.markDelivered('CBR-2026-000118', confirmation);

    expect(JSON.parse(mockApi.history.post[0].data)).toEqual(confirmation);
  });

  it('imports the vendor print response through the encoded batch endpoint', async () => {
    const rows = [{ requestId: 'CBR-1', chequeType: '01' }];
    mockApi.onPost('/vendor-exports/VEX-2026%2F001/print-response').reply(200, {
      batchNumber: 'VEX-2026/001',
      importedRows: 1,
    });

    await exportApi.importPrintResponse('VEX-2026/001', rows);

    expect(mockApi.history.post[0].url).toBe('/vendor-exports/VEX-2026%2F001/print-response');
    expect(JSON.parse(mockApi.history.post[0].data)).toEqual({ rows });
  });

  it('uploads an Excel vendor response as multipart form data', async () => {
    const workbook = new File(['xlsx-content'], 'vendor-response.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    mockApi.onPost('/vendor-exports/VEX-2026%2F001/print-response-file').reply(200, {
      batchNumber: 'VEX-2026/001', importedRows: 1,
    });

    await exportApi.importPrintResponseWorkbook('VEX-2026/001', workbook);

    expect(mockApi.history.post[0].url).toBe('/vendor-exports/VEX-2026%2F001/print-response-file');
    expect(mockApi.history.post[0].data).toBeInstanceOf(FormData);
    expect(mockApi.history.post[0].data.get('file')).toBe(workbook);
  });
});
