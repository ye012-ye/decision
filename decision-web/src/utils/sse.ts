import type { ChatStreamEvent } from '@/types/chat';

export interface ParseResult {
  events: ChatStreamEvent[];
  remainder: string;
}

export function parseSseChunk(chunk: string, buffer = ''): ParseResult {
  const combined = buffer + chunk;
  const blocks = combined.split('\n\n');
  const remainder = blocks.pop() ?? '';
  const events = blocks
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => {
      const lines = block.split('\n');
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n');

      return {
        event: event as ChatStreamEvent['event'],
        data,
      };
    });

  return { events, remainder };
}
