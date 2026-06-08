import { defineStore } from 'pinia';

import { deleteChatSession, getChatMessages, listChatSessions, streamChat } from '@/api/chat';
import type {
  ChatAssistantMessage,
  ChatHistoryMessage,
  ChatMessage,
  ChatProcessType,
  ChatSessionSummary,
} from '@/types/chat';

interface SessionState {
  id: string;
  title: string;
  messages: ChatMessage[];
  messageCount?: number;
  loaded?: boolean;
}

const DEFAULT_SESSION_TITLE = '新会话';
const TITLE_MAX_LEN = 20;

function createSession(title: string): SessionState {
  return {
    id: crypto.randomUUID(),
    title,
    messages: [],
    messageCount: 0,
    loaded: true,
  };
}

const FALLBACK_ASSISTANT_MESSAGE = '暂未获取到回复，请稍后重试。';
const FALLBACK_ASSISTANT_ERROR_MESSAGE = '请求失败，请稍后重试。';

function appendProcessEntry(message: ChatAssistantMessage, type: ChatProcessType, content: string) {
  message.process.push({ id: crypto.randomUUID(), type, content });
}

function sessionFromSummary(summary: ChatSessionSummary): SessionState {
  return {
    id: summary.sessionId,
    title: summary.title || DEFAULT_SESSION_TITLE,
    messages: [],
    messageCount: summary.messageCount,
    loaded: false,
  };
}

function messageFromHistory(message: ChatHistoryMessage): ChatMessage {
  if (message.role === 'user') {
    return {
      id: message.id,
      role: 'user',
      content: message.content,
    };
  }

  return {
    id: message.id,
    role: 'assistant',
    content: message.content,
    status: 'done',
    process: [],
    processExpanded: false,
  };
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    sessions: [createSession(DEFAULT_SESSION_TITLE)],
    activeSessionId: '',
    sending: false,
    abortController: null as AbortController | null,
    historyLoaded: false,
  }),
  getters: {
    activeSession(state) {
      return state.sessions.find((session) => session.id === state.activeSessionId) ?? state.sessions[0];
    },
  },
  actions: {
    async bootstrap() {
      if (!this.activeSessionId) {
        this.activeSessionId = this.sessions[0].id;
      }
      if (!this.historyLoaded) {
        this.historyLoaded = true;
        try {
          const persistedSessions = await listChatSessions();
          if (persistedSessions.length > 0) {
            this.sessions = persistedSessions.map(sessionFromSummary);
            this.activeSessionId = this.sessions[0].id;
            await this.loadSessionMessages(this.activeSessionId);
            return;
          }
        } catch {
          this.historyLoaded = false;
        }
      }
    },
    async activateSession(sessionId: string) {
      this.activeSessionId = sessionId;
      await this.loadSessionMessages(sessionId);
    },
    async loadSessionMessages(sessionId: string) {
      const session = this.sessions.find((item) => item.id === sessionId);
      if (!session || session.loaded || session.messages.length > 0) {
        return;
      }
      const messages = await getChatMessages(sessionId);
      session.messages = messages.map(messageFromHistory);
      session.messageCount = session.messages.length;
      session.loaded = true;
    },
    newConversation() {
      const session = createSession(DEFAULT_SESSION_TITLE);
      this.sessions.unshift(session);
      this.activeSessionId = session.id;
    },
    removeSession(sessionId: string) {
      const index = this.sessions.findIndex((session) => session.id === sessionId);
      if (index === -1) return;

      this.sessions.splice(index, 1);
      deleteChatSession(sessionId).catch(() => {});
      if (this.sessions.length === 0) {
        this.sessions.push(createSession(DEFAULT_SESSION_TITLE));
      }
      if (this.activeSessionId === sessionId) {
        const nextIndex = Math.min(index, this.sessions.length - 1);
        this.activeSessionId = this.sessions[nextIndex].id;
      }
    },
    async sendMessage(message: string) {
      const targetSessionId = this.activeSessionId || this.sessions[0].id;
      await this.bootstrap();
      this.abortController?.abort();
      const controller = new AbortController();
      this.abortController = controller;
      this.sending = true;
      const session = this.sessions.find((item) => item.id === targetSessionId) ?? this.activeSession;

      if (session.messages.length === 0) {
        session.title = message.trim().slice(0, TITLE_MAX_LEN) || DEFAULT_SESSION_TITLE;
      }

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
        routedAgent: undefined,
      };

      session.messages.push(userMessage, assistantMessage);
      session.messageCount = session.messages.length;
      session.loaded = true;
      const assistantMessageId = assistantMessage.id;
      const withAssistantMessage = (apply: (target: ChatAssistantMessage) => void) => {
        const target = session.messages.find(
          (item): item is ChatAssistantMessage => item.id === assistantMessageId && item.role === 'assistant'
        );
        if (!target) return;
        apply(target);
      };

      try {
        await streamChat(
          { sessionId: session.id, message },
          (event) => {
            withAssistantMessage((target) => {
              if (event.event === 'answer') {
                target.content += event.data;
              } else if (event.event === 'route') {
                target.routedAgent = event.data;
                appendProcessEntry(target, 'route', event.data);
              } else if (
                event.event === 'thought' ||
                event.event === 'action' ||
                event.event === 'observation'
              ) {
                appendProcessEntry(target, event.event, event.data);
              } else if (event.event === 'done') {
                if (target.status === 'streaming') target.status = 'done';
                this.sending = false;
                this.abortController = null;
              } else if (event.event === 'error') {
                target.status = 'error';
                target.processExpanded = true;
                this.sending = false;
                this.abortController = null;
                const errorText = event.data.trim() || FALLBACK_ASSISTANT_ERROR_MESSAGE;
                if (!target.content.trim()) target.content = errorText;
                else target.content += `\n${errorText}`;
              }
            });
          },
          controller.signal,
        );
        withAssistantMessage((target) => {
          if (!target.content.trim() && target.status !== 'error') {
            target.content = FALLBACK_ASSISTANT_MESSAGE;
          }
          if (target.status === 'streaming') target.status = 'done';
        });
        session.messageCount = session.messages.length;
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
          if (!target.content.trim()) target.content = errorText;
          else if (errorText) target.content += `\n${errorText}`;
        });
        if (!aborted) throw error;
      } finally {
        session.messageCount = session.messages.length;
        if (this.abortController === controller) {
          this.sending = false;
          this.abortController = null;
        }
      }
    },
    stopStreaming() {
      this.abortController?.abort();
    },
  },
});
