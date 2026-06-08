import type { ChatHistoryMessage, ChatRequest, ChatSessionSummary, ChatStreamEvent } from '@/types/chat';
import { parseSseChunk } from '@/utils/sse';
import { redirectToLogin, requestJson } from './http';
import { authHeader } from './token';

export function listChatSessions() {
  return requestJson<ChatSessionSummary[]>('/api/chat/sessions', {
    method: 'GET',
  });
}

export function getChatMessages(sessionId: string) {
  return requestJson<ChatHistoryMessage[]>(`/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: 'GET',
  });
}

export function deleteChatSession(sessionId: string) {
  return requestJson<void>(`/api/chat/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  });
}

export async function streamChat(
  request: ChatRequest,
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal,
) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authHeader(),
    },
    body: JSON.stringify(request),
    signal,
  });

  if (response.status === 401) {
    redirectToLogin();
    throw new Error('未登录或登录已过期');
  }

  if (!response.ok || !response.body) {
    throw new Error(`聊天请求失败: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    const text = decoder.decode(value, { stream: true });
    const parsed = parseSseChunk(text, buffer);
    buffer = parsed.remainder;
    parsed.events.forEach(onEvent);
  }
}
