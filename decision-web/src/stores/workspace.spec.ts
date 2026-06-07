import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ChatAssistantMessage, ChatMessage, ChatStreamEvent } from '@/types/chat';
import { vi } from 'vitest';

let resumeStream: (() => void) | null = null;
let streamScenario: (onEvent: (event: ChatStreamEvent) => void) => Promise<void> = async () => {};

function createDefaultScenario() {
  return async (onEvent: (event: ChatStreamEvent) => void) => {
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
    onEvent({ event: 'answer', data: '请稍后查收' });
    onEvent({ event: 'done', data: '[DONE]' });
  };
}

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => streamScenario(onEvent)),
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
    streamScenario = createDefaultScenario();
  });

  it('sending creates one user message and one assistant message per streamed turn', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.messages).toHaveLength(2);
    expect(store.activeSession.messages[0]?.role).toBe('user');
    expect(store.activeSession.messages[0]?.content).toBe('客户投诉物流慢');
    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('done');
  });

  it('multiple streamed answer payloads accumulate into one assistant message', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，请稍后查收');
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
  });

  it('session switching during streaming keeps updates on the originating session', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSession = store.activeSession;
    store.sessions.push({ id: crypto.randomUUID(), title: '新会话 2', messages: [] });

    const sendPromise = store.sendMessage('客户投诉物流慢');
    await Promise.resolve();

    store.activateSession(store.sessions[1].id);
    resumeStream?.();
    await sendPromise;

    expect(originalSession.messages).toHaveLength(2);
    const assistantMessage = asAssistantMessage(originalSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，请稍后查收');
    expect(store.sessions[1].messages).toHaveLength(0);
    expect(store.activeSessionId).toBe(store.sessions[1].id);
  });

  it('marks assistant message as error with visible text when stream request throws', async () => {
    streamScenario = async () => {
      throw new Error('上游连接失败');
    };

    const store = useWorkspaceStore();
    await expect(store.sendMessage('客户投诉物流慢')).rejects.toThrow('上游连接失败');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('error');
    expect(assistantMessage.content).toContain('上游连接失败');
    expect(assistantMessage.processExpanded).toBe(true);
  });

  it('shows SSE error event text in assistant message content', async () => {
    streamScenario = async (onEvent) => {
      onEvent({ event: 'error', data: '工具调用失败: logistics timeout' });
    };

    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('error');
    expect(assistantMessage.content).toContain('工具调用失败');
    expect(assistantMessage.processExpanded).toBe(true);
  });

  it('uses fallback assistant text when stream completes without answer content', async () => {
    streamScenario = async (onEvent) => {
      onEvent({ event: 'thought', data: '正在分析问题' });
      onEvent({ event: 'done', data: '[DONE]' });
    };

    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('done');
    expect(assistantMessage.content).toBe('暂未获取到回复，请稍后重试。');
  });

  it('newConversation creates a fresh active session at the front', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    const firstId = store.activeSessionId;

    store.newConversation();

    expect(store.sessions.length).toBe(2);
    expect(store.sessions[0].id).toBe(store.activeSessionId);
    expect(store.activeSessionId).not.toBe(firstId);
    expect(store.activeSession.messages).toHaveLength(0);
  });

  it('removeSession drops a session and keeps at least one', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    store.newConversation();
    const activeId = store.activeSessionId;

    store.removeSession(activeId);
    expect(store.sessions.some((s) => s.id === activeId)).toBe(false);
    expect(store.sessions.length).toBe(1);

    const lastRemaining = store.sessions[0].id;
    store.removeSession(lastRemaining);
    expect(store.sessions.length).toBe(1);
    expect(store.activeSessionId).toBe(store.sessions[0].id);
  });

  it('first user message becomes the session title', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('帮我查询最近的订单状态并跟进');
    expect(store.activeSession.title).toBe('帮我查询最近的订单状态并跟进'.slice(0, 20));
  });
});
