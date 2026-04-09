import type { ResultEnvelope } from '@/types/api';

export async function requestJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = (await response.json()) as ResultEnvelope<T>;
  if (payload.code !== 200) {
    throw new Error(payload.msg);
  }

  return payload.data;
}
