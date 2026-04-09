import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => {
    onEvent({ event: 'thought', data: '需要查询物流' });
    onEvent({ event: 'action', data: 'callExternalApiTool | {"service":"logistics"}' });
    onEvent({ event: 'answer', data: '物流已更新，已创建工单 WO20260409001' });
    onEvent({ event: 'done', data: '[DONE]' });
  }),
}));

import { useWorkspaceStore } from './workspace';

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('streams chat events into the active session timeline', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.events).toHaveLength(5);
    expect(store.activeSession.events[store.activeSession.events.length - 1]?.type).toBe('done');
    expect(store.context.ticketOrderNo).toBe('WO20260409001');
  });
});
