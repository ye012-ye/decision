import type { ResultEnvelope } from '@/types/api';
import { authHeader, clearToken } from './token';

export function redirectToLogin(): void {
  clearToken();
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

export async function readJsonEnvelope<T>(response: Response): Promise<ResultEnvelope<T> | null> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.toLowerCase().includes('application/json')) {
    return null;
  }

  try {
    return (await response.json()) as ResultEnvelope<T>;
  } catch {
    return null;
  }
}

export async function requestJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...authHeader(),
      ...(init?.headers ?? {}),
    },
  });

  if (response.status === 401) {
    redirectToLogin();
    throw new Error('未登录或登录已过期');
  }

  const payload = await readJsonEnvelope<T>(response);
  if (payload) {
    if (!response.ok || payload.code !== 200) {
      throw new Error(payload.msg);
    }

    return payload.data;
  }

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  throw new Error(`HTTP ${response.status}`);
}
