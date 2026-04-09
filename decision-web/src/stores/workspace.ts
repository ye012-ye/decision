import { defineStore } from 'pinia';

import { streamChat } from '@/api/chat';
import { createTicket } from '@/api/tickets';
import { extractOrderNo } from '@/utils/extractors';

interface TimelineEvent {
  id: string;
  type: string;
  content: string;
}

interface SessionState {
  id: string;
  title: string;
  events: TimelineEvent[];
}

interface WorkspaceContext {
  ticketOrderNo: string;
  activeTab: string;
}

function createSession(title: string): SessionState {
  return {
    id: crypto.randomUUID(),
    title,
    events: [],
  };
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    sessions: [createSession('新会话')],
    activeSessionId: '',
    sending: false,
    context: {
      ticketOrderNo: '',
      activeTab: 'ticket',
    } as WorkspaceContext,
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
    async sendMessage(message: string) {
      this.bootstrap();
      this.sending = true;

      this.activeSession.events.push({
        id: crypto.randomUUID(),
        type: 'user',
        content: message,
      });

      try {
        await streamChat(
          {
            sessionId: this.activeSession.id,
            message,
          },
          (event) => {
            this.activeSession.events.push({
              id: crypto.randomUUID(),
              type: event.event,
              content: event.data,
            });

            const matchedOrderNo = extractOrderNo(event.data);
            if (matchedOrderNo) {
              this.context.ticketOrderNo = matchedOrderNo;
              this.context.activeTab = 'ticket';
            }
          }
        );
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
      const ticket = await createTicket({
        ...payload,
        sessionId: this.activeSession.id,
      });

      this.context.ticketOrderNo = ticket.orderNo;
      this.context.activeTab = 'ticket';
      return ticket;
    },
  },
});
