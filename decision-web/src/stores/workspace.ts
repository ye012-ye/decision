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
    events: [],
    context: {
      ticketOrderNo: '',
      activeTab: 'ticket',
    },
  };
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
    async sendMessage(message: string) {
      this.bootstrap();
      this.sending = true;
      const session = this.activeSession;

      session.events.push({
        id: crypto.randomUUID(),
        type: 'user',
        content: message,
      });

      try {
        await streamChat(
          {
            sessionId: session.id,
            message,
          },
          (event) => {
            session.events.push({
              id: crypto.randomUUID(),
              type: event.event,
              content: event.data,
            });

            const matchedOrderNo = extractOrderNo(event.data);
            if (matchedOrderNo) {
              session.context.ticketOrderNo = matchedOrderNo;
              session.context.activeTab = 'ticket';
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
