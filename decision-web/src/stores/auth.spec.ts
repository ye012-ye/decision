import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/auth', () => ({
  login: vi.fn(async (username: string) => ({
    token: 'jwt-token-123',
    username,
    nickname: '管理员',
  })),
  fetchMe: vi.fn(async () => ({ username: 'admin', nickname: '管理员', role: 'ADMIN' })),
}));

import { getToken } from '@/api/token';
import { useAuthStore } from './auth';

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('stores token and user after login', async () => {
    const store = useAuthStore();
    await store.login('admin', 'admin123');

    expect(store.token).toBe('jwt-token-123');
    expect(store.user).toEqual({ username: 'admin', nickname: '管理员' });
    expect(store.isAuthenticated).toBe(true);
    expect(getToken()).toBe('jwt-token-123');
  });

  it('clears token and user on logout', async () => {
    const store = useAuthStore();
    await store.login('admin', 'admin123');
    store.logout();

    expect(store.token).toBeNull();
    expect(store.user).toBeNull();
    expect(store.isAuthenticated).toBe(false);
    expect(getToken()).toBeNull();
  });
});
