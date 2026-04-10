import { fireEvent, render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';

import type { ChatAssistantMessage, ChatMessage, ChatUserMessage } from '@/types/chat';

import ChatTimeline from './ChatTimeline.vue';

const baseUserMessage: ChatUserMessage = {
  id: 'user-1',
  role: 'user',
  content: '客户投诉物流慢',
};

const baseAssistantMessage: ChatAssistantMessage = {
  id: 'assistant-1',
  role: 'assistant',
  content: '我先帮你查一下',
  status: 'done',
  process: [
    {
      id: 'process-1',
      type: 'thought',
      content: '检索工单与物流状态',
    },
  ],
  processExpanded: false,
};

const baseMessages: ChatMessage[] = [baseUserMessage, baseAssistantMessage];

describe('ChatTimeline', () => {
  it('renders user and assistant messages as primary bubbles', () => {
    const { container } = render(ChatTimeline, {
      props: {
        messages: baseMessages,
      },
    });

    expect(screen.getByText('客户投诉物流慢')).toBeInTheDocument();
    expect(screen.getByText('我先帮你查一下')).toBeInTheDocument();
    expect(container.querySelector('[data-message-id="user-1"] .chat-timeline__bubble')).toBeInTheDocument();
    expect(
      container.querySelector('[data-message-id="assistant-1"] .chat-timeline__bubble')
    ).toBeInTheDocument();
  });

  it('shows collapsed process disclosure for assistant process entries and emits id on click', async () => {
    const view = render(ChatTimeline, {
      props: {
        messages: baseMessages,
      },
    });

    const disclosure = screen.getByRole('button', { name: '展开过程' });
    expect(disclosure).toBeInTheDocument();
    expect(disclosure).toHaveAttribute('aria-expanded', 'false');
    expect(disclosure).toHaveAttribute('aria-controls', 'chat-process-assistant-1');
    expect(screen.queryByText('检索工单与物流状态')).not.toBeInTheDocument();

    await fireEvent.click(disclosure);

    expect(view.emitted('toggle-process')).toEqual([['assistant-1']]);
  });

  it('shows process rows when rerendered expanded', async () => {
    const { rerender } = render(ChatTimeline, {
      props: {
        messages: baseMessages,
      },
    });

    expect(screen.queryByText('检索工单与物流状态')).not.toBeInTheDocument();

    await rerender({
      messages: [
        baseUserMessage,
        {
          ...baseAssistantMessage,
          processExpanded: true,
        },
      ],
    });

    const disclosure = screen.getByRole('button', { name: '收起过程' });
    expect(disclosure).toHaveAttribute('aria-expanded', 'true');
    expect(disclosure).toHaveAttribute('aria-controls', 'chat-process-assistant-1');
    expect(screen.getByText('检索工单与物流状态')).toBeInTheDocument();
  });

  it('keeps process details visible for assistant error states when expanded', () => {
    render(ChatTimeline, {
      props: {
        messages: [
          baseUserMessage,
          {
            ...baseAssistantMessage,
            id: 'assistant-err-1',
            status: 'error',
            processExpanded: true,
          },
        ],
      },
    });

    expect(screen.getByRole('button', { name: '收起过程' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('检索工单与物流状态')).toBeInTheDocument();
  });
});
