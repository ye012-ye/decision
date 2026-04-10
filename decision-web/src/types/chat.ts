export type ChatEventType = 'thought' | 'action' | 'observation' | 'answer' | 'done' | 'error';

export interface ChatStreamEvent {
  event: ChatEventType;
  data: string;
}

export type ChatProcessType = Extract<ChatEventType, 'thought' | 'action' | 'observation'>;

export interface ChatProcessEntry {
  id: string;
  type: ChatProcessType;
  content: string;
}

export type AssistantMessageStatus = 'streaming' | 'done' | 'error';

export interface ChatUserMessage {
  id: string;
  role: 'user';
  content: string;
}

export interface ChatAssistantMessage {
  id: string;
  role: 'assistant';
  content: string;
  status: AssistantMessageStatus;
  process: ChatProcessEntry[];
  processExpanded: boolean;
}

export type ChatMessage = ChatUserMessage | ChatAssistantMessage;

export interface ChatRequest {
  sessionId: string;
  message: string;
}
