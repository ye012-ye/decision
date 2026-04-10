import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatAssistantMessage, ChatMessage } from '@/types/chat';

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
    onEvent({ event: 'observation', data: '查询到物流延迟 2 天' });
    onEvent({ event: 'answer', data: '物流已更新，' });
    onEvent({ event: 'answer', data: '已创建工单 WO20260409001' });
    onEvent({ event: 'done', data: '[DONE]' });
  }),
}));

vi.mock('@/api/tickets', () => ({
  createTicket: vi.fn(async () => {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 0);
    });

    return {
      orderNo: 'WO20260409099',
    };
  }),
}));

import { useWorkspaceStore } from './workspace';

function asAssistantMessage(message: ChatMessage | undefined): ChatAssistantMessage {
  expect(message?.role).toBe('assistant');
  return message as ChatAssistantMessage;
}

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resumeStream = null;
  });

  it('sending creates one user message and one assistant message per streamed turn', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.messages).toHaveLength(2);
    expect(store.activeSession.messages[0]?.role).toBe('user');
    expect(store.activeSession.messages[0]?.content).toBe('客户投诉物流慢');
    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('done');
    expect(store.activeSession.context.ticketOrderNo).toBe('WO20260409001');
  });

  it('multiple streamed answer payloads accumulate into one assistant message', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，已创建工单 WO20260409001');
  });

  it('process entries collect under that assistant message', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.process.map((entry) => entry.type)).toEqual([
      'thought',
      'action',
      'observation',
    ]);
    expect(assistantMessage.process.map((entry) => entry.content)).toEqual([
      '需要查询物流',
      'callExternalApiTool | {"service":"logistics"}',
      '查询到物流延迟 2 天',
    ]);
  });

  it('session switching during streaming keeps updates on the originating session', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSessionId = store.activeSessionId;
    const originalSession = store.activeSession;
    store.sessions.push({
      id: crypto.randomUUID(),
      title: '新会话 2',
      messages: [],
      context: {
        ticketOrderNo: '',
        activeTab: 'ticket',
      },
    });

    const sendPromise = store.sendMessage('客户投诉物流慢');
    await Promise.resolve();

    store.activateSession(store.sessions[1].id);
    resumeStream?.();

    await sendPromise;

    expect(originalSession.messages).toHaveLength(2);
    expect(originalSession.messages[0]?.role).toBe('user');
    const assistantMessage = asAssistantMessage(originalSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，已创建工单 WO20260409001');
    expect(store.sessions[1].messages).toHaveLength(0);
    expect(store.activeSessionId).toBe(store.sessions[1].id);
    expect(originalSessionId).toBe(originalSession.id);
    expect(store.sessions[1].context.ticketOrderNo).toBe('');
    expect(store.sessions[1].context.activeTab).toBe('ticket');
    expect(originalSession.context.ticketOrderNo).toBe('WO20260409001');
  });

  it('keeps ticket context on the session that triggered ticket creation', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSession = store.activeSession;
    store.sessions.push({
      id: crypto.randomUUID(),
      title: '新会话 2',
      messages: [],
      context: {
        ticketOrderNo: '',
        activeTab: 'ticket',
      },
    });

    store.activateSession(store.sessions[1].id);

    const createPromise = store.createTicketFromContext({
      type: 'LOGISTICS',
      title: '物流异常跟进',
      description: '客户反馈物流慢',
      customerId: 'CUS-10086',
      priority: 'HIGH',
    });

    await Promise.resolve();
    store.activateSession(originalSession.id);

    const ticket = await createPromise;

    expect(ticket.orderNo).toBe('WO20260409099');
    expect(store.sessions[1].context.ticketOrderNo).toBe('WO20260409099');
    expect(originalSession.context.ticketOrderNo).toBe('');
    expect(store.activeSession.context.ticketOrderNo).toBe('');
  });
});
