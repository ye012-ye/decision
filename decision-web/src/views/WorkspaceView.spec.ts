import { fireEvent, render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatStreamEvent } from '@/types/chat';

import WorkspaceView from './WorkspaceView.vue';

let streamScenario: (onEvent: (event: ChatStreamEvent) => void) => Promise<void> = async () => {};

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => streamScenario(onEvent)),
}));

vi.mock('@/api/tickets', () => ({
  createTicket: vi.fn(async () => ({ orderNo: 'WO20260409099' })),
}));

describe('WorkspaceView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    streamScenario = async (onEvent) => {
      onEvent({ event: 'thought', data: '已接收客户诉求' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '当前工单 WO20260409001 已' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '进入处理流' });
      onEvent({ event: 'action', data: '已创建工单 WO20260409001' });
      onEvent({ event: 'done', data: '[DONE]' });
    };
  });

  it('renders streamed assistant content and process entries as they arrive', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);

    render(WorkspaceView, {
      global: {
        plugins: [pinia],
      },
    });

    await fireEvent.update(screen.getByLabelText('输入客户诉求'), '客户反馈物流延迟，请帮我跟进。');
    await fireEvent.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('客户反馈物流延迟，请帮我跟进。')).toBeInTheDocument();
    expect(await screen.findByText('当前工单 WO20260409001 已进入处理流')).toBeInTheDocument();

    await fireEvent.click(await screen.findByRole('button', { name: '展开过程' }));

    expect(await screen.findByText('已接收客户诉求')).toBeInTheDocument();
    expect(await screen.findByText('已创建工单 WO20260409001')).toBeInTheDocument();
  });
});
