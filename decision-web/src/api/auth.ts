import type { AuthUser, LoginResponse } from '@/types/auth';
import { requestJson } from './http';

export function login(username: string, password: string) {
  return requestJson<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function fetchMe() {
  return requestJson<AuthUser>('/api/auth/me');
}
