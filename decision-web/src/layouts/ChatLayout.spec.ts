import { render, screen, waitFor } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatStreamEvent } from '@/types/chat';
import { useWorkspaceStore } from '@/stores/workspace';
import ChatLayout from './ChatLayout.vue';

let streamScenario: (onEvent: (event: ChatStreamEvent) => void) => Promise<void> = async () => {};

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => streamScenario(onEvent)),
}));

describe('ChatLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    streamScenario = async (onEvent) => {
      onEvent({ event: 'thought', data: '已接收客户诉求' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '已经' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '处理完成' });
      onEvent({ event: 'done', data: '[DONE]' });
    };
  });

  it('renders streamed assistant content and the process trace', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    render(ChatLayout, { global: { plugins: [pinia] } });

    const store = useWorkspaceStore();
    const sendPromise = store.sendMessage('帮我处理一下');
    await waitFor(() => {
      expect(screen.getAllByText('帮我处理一下').length).toBeGreaterThanOrEqual(1);
    });
    await sendPromise;

    expect(screen.getByText('已经处理完成')).toBeInTheDocument();
    expect(screen.getByTestId('chat-process-trace')).toBeInTheDocument();
  });
});
