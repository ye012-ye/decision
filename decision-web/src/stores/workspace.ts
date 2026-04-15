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
const FALLBACK_ASSISTANT_ERROR_MESSAGE = '请求失败，请稍后重试。';

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
    abortController: null as AbortController | null,
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
      this.abortController?.abort();
      const controller = new AbortController();
      this.abortController = controller;
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
      const assistantMessageId = assistantMessage.id;
      const withAssistantMessage = (apply: (target: ChatAssistantMessage) => void) => {
        const target = session.messages.find(
          (item): item is ChatAssistantMessage => item.id === assistantMessageId && item.role === 'assistant'
        );
        if (!target) {
          return;
        }

        apply(target);
      };

      const updateTicketContextFromText = (text: string) => {
        const matchedOrderNo = extractOrderNo(text);
        if (!matchedOrderNo) {
          return;
        }

        session.context.ticketOrderNo = matchedOrderNo;
        session.context.activeTab = 'ticket';
      };

      try {
        await streamChat(
          {
            sessionId: session.id,
            message,
          },
          (event) => {
            withAssistantMessage((target) => {
              if (event.event === 'answer') {
                target.content += event.data;
                updateTicketContextFromText(target.content);
              } else if (
                event.event === 'thought' ||
                event.event === 'action' ||
                event.event === 'observation'
              ) {
                appendProcessEntry(target, event.event, event.data);
              } else if (event.event === 'done') {
                if (target.status === 'streaming') {
                  target.status = 'done';
                }
              } else if (event.event === 'error') {
                target.status = 'error';
                target.processExpanded = true;
                const errorText = event.data.trim() || FALLBACK_ASSISTANT_ERROR_MESSAGE;
                if (!target.content.trim()) {
                  target.content = errorText;
                } else {
                  target.content += `\n${errorText}`;
                }
              }
            });

            updateTicketContextFromText(event.data);
          },
          controller.signal,
        );
        withAssistantMessage((target) => {
          if (!target.content.trim() && target.status !== 'error') {
            target.content = FALLBACK_ASSISTANT_MESSAGE;
          }
          if (target.status === 'streaming') {
            target.status = 'done';
          }
        });
      } catch (error) {
        const aborted = error instanceof DOMException && error.name === 'AbortError';
        withAssistantMessage((target) => {
          if (aborted) {
            target.status = 'done';
            if (!target.content.trim()) target.content = '（已停止）';
            return;
          }
          target.status = 'error';
          target.processExpanded = true;
          const rawErrorText = error instanceof Error ? error.message.trim() : '';
          const errorText = rawErrorText || FALLBACK_ASSISTANT_ERROR_MESSAGE;
          if (!target.content.trim()) {
            target.content = errorText;
          } else if (errorText) {
            target.content += `\n${errorText}`;
          }
        });
        if (!aborted) throw error;
      } finally {
        this.sending = false;
        this.abortController = null;
      }
    },
    stopStreaming() {
      this.abortController?.abort();
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
