import type { ChatRequest, ChatStreamEvent } from '@/types/chat';
import { parseSseChunk } from '@/utils/sse';

export async function streamChat(request: ChatRequest, onEvent: (event: ChatStreamEvent) => void) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(request),
  });

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
