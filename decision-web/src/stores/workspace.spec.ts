import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let resumeStream: (() => void) | null = null;

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => {
    onEvent({ event: 'thought', data: '需要查询物流' });
    await new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, 0);
      resumeStream = () => {
        clearTimeout(timer);
        resolve();
      };
    });
    onEvent({ event: 'action', data: 'callExternalApiTool | {"service":"logistics"}' });
    onEvent({ event: 'answer', data: '物流已更新，已创建工单 WO20260409001' });
    onEvent({ event: 'done', data: '[DONE]' });
  }),
}));

import { useWorkspaceStore } from './workspace';

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resumeStream = null;
  });

  it('streams chat events into the active session timeline', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.events).toHaveLength(5);
    expect(store.activeSession.events[store.activeSession.events.length - 1]?.type).toBe('done');
    expect(store.context.ticketOrderNo).toBe('WO20260409001');
  });

  it('keeps streamed events on the session that started the message when active session changes mid-stream', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSessionId = store.activeSessionId;
    const originalSession = store.activeSession;
    store.sessions.push({
      id: crypto.randomUUID(),
      title: '新会话 2',
      events: [],
    });

    const sendPromise = store.sendMessage('客户投诉物流慢');
    await Promise.resolve();

    store.activateSession(store.sessions[1].id);
    resumeStream?.();

    await sendPromise;

    expect(originalSession.events.map((event) => event.type)).toEqual([
      'user',
      'thought',
      'action',
      'answer',
      'done',
    ]);
    expect(store.sessions[1].events).toHaveLength(0);
    expect(store.activeSessionId).toBe(store.sessions[1].id);
    expect(originalSessionId).toBe(originalSession.id);
  });
});
