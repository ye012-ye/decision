import { afterEach, describe, expect, it, vi } from 'vitest';

import { requestJson } from '@/api/http';
import { uploadDocument } from '@/api/knowledge';
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

describe('envelope-aware request helpers', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('surfaces backend message for non-2xx JSON envelopes', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 400, msg: '校验失败', data: null }), {
          status: 400,
          headers: {
            'Content-Type': 'application/json',
          },
        })
      )
    );

    await expect(requestJson('/api/test')).rejects.toThrow('校验失败');
  });

  it('surfaces backend message for upload failures', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 400, msg: '上传参数错误', data: null }), {
          status: 400,
          headers: {
            'Content-Type': 'application/json',
          },
        })
      )
    );

    await expect(
      uploadDocument('kb-1', new File(['hello'], 'hello.txt'))
    ).rejects.toThrow('上传参数错误');
  });
});
