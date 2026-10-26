import { describe, expect, it } from 'vitest';
import { validateEntrustToken } from './authValidation';

describe('Entrust token validation', () => {
  it('accepts numeric tokens between six and ten digits', () => {
    expect(validateEntrustToken('123456')).toBe('');
    expect(validateEntrustToken('12345678')).toBe('');
    expect(validateEntrustToken('1234567890')).toBe('');
  });

  it('rejects tokens outside the length range or containing non-digits', () => {
    expect(validateEntrustToken('12345')).toMatch(/between 6 and 10 digits/i);
    expect(validateEntrustToken('12345678901')).toMatch(/between 6 and 10 digits/i);
    expect(validateEntrustToken('12345A')).toMatch(/between 6 and 10 digits/i);
  });
});
