import { afterEach, describe, expect, it } from 'vitest';

import { authHeader, clearToken, getToken, setToken } from './token';

describe('token storage', () => {
  afterEach(() => {
    clearToken();
  });

  it('returns null and empty header when no token stored', () => {
    expect(getToken()).toBeNull();
    expect(authHeader()).toEqual({});
  });

  it('stores and reads the token', () => {
    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
    expect(authHeader()).toEqual({ Authorization: 'Bearer abc.def.ghi' });
  });

  it('clears the token', () => {
    setToken('abc.def.ghi');
    clearToken();
    expect(getToken()).toBeNull();
  });
});
