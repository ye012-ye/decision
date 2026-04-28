export type ChatEventType =
  | 'route'
  | 'thought'
  | 'action'
  | 'observation'
  | 'answer'
  | 'done'
  | 'error';

export interface ChatStreamEvent {
  event: ChatEventType;
  data: string;
}

export type ChatProcessType = Extract<
  ChatEventType,
  'route' | 'thought' | 'action' | 'observation'
>;

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
  /** 路由器选中的子 agent 名（最近一次 route 事件的 data） */
  routedAgent?: string;
}

export type ChatMessage = ChatUserMessage | ChatAssistantMessage;

export interface ChatRequest {
  sessionId: string;
  message: string;
}
