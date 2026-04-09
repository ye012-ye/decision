import { describe, expect, it } from 'vitest';

import { parseSseChunk } from './sse';

describe('parseSseChunk', () => {
  it('returns complete events and preserves tail buffer', () => {
    const result = parseSseChunk(
      'event:thought\ndata:需要先查订单\n\n' +
        'event:answer\ndata:最终回复',
      ''
    );

    expect(result.events).toEqual([
      { event: 'thought', data: '需要先查订单' },
    ]);
    expect(result.remainder).toBe('event:answer\ndata:最终回复');
  });

  it('supports multi-line data payloads', () => {
    const result = parseSseChunk(
      'event:observation\ndata:{"a":1}\ndata:{"b":2}\n\n',
      ''
    );

    expect(result.events[0]).toEqual({
      event: 'observation',
      data: '{"a":1}\n{"b":2}',
    });
  });
});
