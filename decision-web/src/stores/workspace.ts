import { defineStore } from 'pinia';

import { streamChat } from '@/api/chat';
import type { ChatAssistantMessage, ChatMessage, ChatProcessType } from '@/types/chat';
import { createTicket } from '@/api/tickets';
import { extractOrderNo } from '@/utils/extractors';

interface SessionState {
  id: string;
  title: string;
  messages: ChatMessage[];
  context: WorkspaceContext;
}

interface WorkspaceContext {
  ticketOrderNo: string;
  activeTab: string;
}

function createSession(title: string): SessionState {
  return {
    id: crypto.randomUUID(),
    title,
    messages: [],
    context: {
      ticketOrderNo: '',
      activeTab: 'ticket',
    },
  };
}

const FALLBACK_ASSISTANT_MESSAGE = '暂未获取到回复，请稍后重试。';

function appendProcessEntry(message: ChatAssistantMessage, type: ChatProcessType, content: string) {
  message.process.push({
    id: crypto.randomUUID(),
    type,
    content,
  });
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    sessions: [createSession('新会话')],
    activeSessionId: '',
    sending: false,
  }),
  getters: {
    activeSession(state) {
      return state.sessions.find((session) => session.id === state.activeSessionId) ?? state.sessions[0];
    },
  },
  actions: {
    bootstrap() {
      if (!this.activeSessionId) {
        this.activeSessionId = this.sessions[0].id;
      }
    },
    activateSession(sessionId: string) {
      this.activeSessionId = sessionId;
    },
    toggleProcess(messageId: string) {
      this.bootstrap();
      const session = this.activeSession;
      const message = session.messages.find((item) => item.id === messageId && item.role === 'assistant');
      if (!message || message.role !== 'assistant') {
        return;
      }

      message.processExpanded = !message.processExpanded;
    },
    async sendMessage(message: string) {
      this.bootstrap();
      this.sending = true;
      const session = this.activeSession;
      const userMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: message,
      };
      const assistantMessage: ChatAssistantMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '',
        status: 'streaming',
        process: [],
        processExpanded: false,
      };

      session.messages.push(userMessage, assistantMessage);

      try {
        await streamChat(
          {
            sessionId: session.id,
            message,
          },
          (event) => {
            if (event.event === 'answer') {
              assistantMessage.content += event.data;
            } else if (
              event.event === 'thought' ||
              event.event === 'action' ||
              event.event === 'observation'
            ) {
              appendProcessEntry(assistantMessage, event.event, event.data);
            } else if (event.event === 'done') {
              assistantMessage.status = 'done';
            } else if (event.event === 'error') {
              assistantMessage.status = 'error';
              assistantMessage.processExpanded = true;
            }

            const matchedOrderNo = extractOrderNo(event.data);
            if (matchedOrderNo) {
              session.context.ticketOrderNo = matchedOrderNo;
              session.context.activeTab = 'ticket';
            }
          }
        );
        if (assistantMessage.status === 'streaming') {
          if (!assistantMessage.content.trim()) {
            assistantMessage.content = FALLBACK_ASSISTANT_MESSAGE;
          }
          assistantMessage.status = 'done';
        }
      } finally {
        this.sending = false;
      }
    },
    async createTicketFromContext(payload: {
      type: 'ORDER' | 'LOGISTICS' | 'ACCOUNT' | 'TECH_FAULT' | 'CONSULTATION' | 'OTHER';
      priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
      title: string;
      description: string;
      customerId: string;
    }) {
      const session = this.activeSession;
      const ticket = await createTicket({
        ...payload,
        sessionId: session.id,
      });

      session.context.ticketOrderNo = ticket.orderNo;
      session.context.activeTab = 'ticket';
      return ticket;
    },
  },
});
