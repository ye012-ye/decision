export type ChatEventType = 'thought' | 'action' | 'observation' | 'answer' | 'done' | 'error';

export interface ChatStreamEvent {
  event: ChatEventType;
  data: string;
}

export interface ChatRequest {
  sessionId: string;
  message: string;
}
