import { render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';

import type { ChatAssistantMessage, ChatMessage, ChatUserMessage } from '@/types/chat';
import ChatTimeline from './ChatTimeline.vue';

const userMsg: ChatUserMessage = { id: 'user-1', role: 'user', content: '客户投诉物流慢' };
const assistantMsg: ChatAssistantMessage = {
  id: 'assistant-1',
  role: 'assistant',
  content: '我先帮你查一下',
  status: 'done',
  process: [{ id: 'p1', type: 'thought', content: '检索工单' }],
  processExpanded: false,
};

describe('ChatTimeline', () => {
  it('renders empty state with suggestions when no messages', () => {
    render(ChatTimeline, {
      props: { messages: [] },
      global: { plugins: [createPinia()] },
    });
    expect(screen.getByText('开始一段对话')).toBeInTheDocument();
    expect(screen.getByText('订单 A2025 的物流状态？')).toBeInTheDocument();
  });

  it('renders user and assistant messages via ChatMessage', () => {
    const messages: ChatMessage[] = [userMsg, assistantMsg];
    render(ChatTimeline, {
      props: { messages },
      global: { plugins: [createPinia()] },
    });
    expect(screen.getByText('客户投诉物流慢')).toBeInTheDocument();
    expect(screen.getByText('我先帮你查一下')).toBeInTheDocument();
    expect(screen.getByTestId('chat-bubble-user-1')).toBeInTheDocument();
    expect(screen.getByTestId('chat-bubble-assistant-1')).toBeInTheDocument();
  });
});
