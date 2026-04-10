# Workspace Chat SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the workspace chat from an SSE event log into a ChatGPT-like streaming conversation with one growing assistant bubble per turn and a collapsible process panel for `thought`, `action`, and `observation`.

**Architecture:** Keep the existing `/api/chat/stream` contract and SSE parser, but change the workspace store to track chat messages instead of raw events. Render the center column from a message list, move intermediate events into a collapsible assistant-process panel, and preserve session isolation and ticket extraction by mutating the assistant draft captured when the stream starts.

**Tech Stack:** Vue 3, Pinia, TypeScript, Vite, Vitest, Testing Library, Playwright

---

## File Structure

- Modify: `src/types/chat.ts`
  - Keep SSE event contracts and add shared chat message types for the workspace UI.
- Modify: `src/stores/workspace.ts`
  - Replace flat `events` storage with message-based session state and route streamed chunks into the active assistant draft for the originating session.
- Modify: `src/stores/workspace.spec.ts`
  - Cover message creation, answer accumulation, process collection, session isolation, and ticket extraction.
- Modify: `src/components/workspace/ChatTimeline.vue`
  - Render user and assistant bubbles, process disclosure, and auto-scroll behavior.
- Create: `src/components/workspace/ChatTimeline.spec.ts`
  - Verify disclosure rendering, toggle behavior, and assistant error visibility.
- Modify: `src/components/workspace/ComposerBar.vue`
  - Adjust structure for a quieter ChatGPT-like composer layout.
- Create: `src/components/workspace/ComposerBar.spec.ts`
  - Verify the updated helper copy and idle composer action.
- Modify: `src/views/WorkspaceView.vue`
  - Pass `messages` instead of `events` and wire the process toggle event from the timeline to the store.
- Modify: `src/styles/theme.css`
  - Replace event-log styles with message bubble, process panel, and composer styles.
- Modify: `tests/e2e/console.spec.ts`
  - Assert the new chat surface, disclosure flow, and preserved work-order linkage.

### Task 1: Convert The Workspace Store To Message-Based Streaming

**Files:**
- Modify: `src/types/chat.ts`
- Modify: `src/stores/workspace.spec.ts`
- Modify: `src/stores/workspace.ts`

- [ ] **Step 1: Write the failing store tests for message-based turns**

Replace the current event-log assertions in `src/stores/workspace.spec.ts` with message-oriented assertions and split the mocked assistant answer into multiple chunks:

```ts
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let resumeStream: (() => void) | null = null;

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => {
    onEvent({ event: 'thought', data: '需要查询物流' });
    await new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, 0);
      resumeStream = () => {
        clearTimeout(timer);
        resolve();
      };
    });
    onEvent({ event: 'action', data: 'callExternalApiTool | {"service":"logistics"}' });
    onEvent({ event: 'answer', data: '物流已更新，' });
    onEvent({ event: 'answer', data: '已创建工单 WO20260409001' });
    onEvent({ event: 'done', data: '[DONE]' });
  }),
}));

vi.mock('@/api/tickets', () => ({
  createTicket: vi.fn(async () => {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, 0);
    });

    return {
      orderNo: 'WO20260409099',
    };
  }),
}));

import { useWorkspaceStore } from './workspace';

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resumeStream = null;
  });

  it('creates one user message and one assistant message per streamed turn', async () => {
    const store = useWorkspaceStore();

    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.messages).toHaveLength(2);
    expect(store.activeSession.messages[0]).toMatchObject({
      role: 'user',
      content: '客户投诉物流慢',
    });
    expect(store.activeSession.messages[1]).toMatchObject({
      role: 'assistant',
      content: '物流已更新，已创建工单 WO20260409001',
      status: 'done',
    });
    expect(store.activeSession.messages[1]?.processEntries.map((entry) => entry.type)).toEqual([
      'thought',
      'action',
    ]);
    expect(store.activeSession.context.ticketOrderNo).toBe('WO20260409001');
  });

  it('keeps streamed updates on the session that started the message when the active session changes', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSessionId = store.activeSessionId;
    const originalSession = store.activeSession;
    store.sessions.push({
      id: crypto.randomUUID(),
      title: '新会话 2',
      messages: [],
      context: {
        ticketOrderNo: '',
        activeTab: 'ticket',
      },
    });

    const sendPromise = store.sendMessage('客户投诉物流慢');
    await Promise.resolve();

    store.activateSession(store.sessions[1].id);
    resumeStream?.();

    await sendPromise;

    expect(originalSession.messages).toHaveLength(2);
    expect(originalSession.messages[1]).toMatchObject({
      role: 'assistant',
      content: '物流已更新，已创建工单 WO20260409001',
      status: 'done',
    });
    expect(store.sessions[1].messages).toHaveLength(0);
    expect(store.activeSessionId).toBe(store.sessions[1].id);
    expect(originalSessionId).toBe(originalSession.id);
    expect(originalSession.context.ticketOrderNo).toBe('WO20260409001');
  });

  it('keeps ticket context on the session that triggered ticket creation', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSession = store.activeSession;
    store.sessions.push({
      id: crypto.randomUUID(),
      title: '新会话 2',
      messages: [],
      context: {
        ticketOrderNo: '',
        activeTab: 'ticket',
      },
    });

    store.activateSession(store.sessions[1].id);

    const createPromise = store.createTicketFromContext({
      type: 'LOGISTICS',
      title: '物流异常跟进',
      description: '客户反馈物流慢',
      customerId: 'CUS-10086',
      priority: 'HIGH',
    });

    await Promise.resolve();
    store.activateSession(originalSession.id);

    const ticket = await createPromise;

    expect(ticket.orderNo).toBe('WO20260409099');
    expect(store.sessions[1].context.ticketOrderNo).toBe('WO20260409099');
    expect(originalSession.context.ticketOrderNo).toBe('');
  });
});
```

- [ ] **Step 2: Run the store test file and verify it fails for the right reason**

Run:

```bash
npm test -- src/stores/workspace.spec.ts
```

Expected:

Expected: the file fails because the current store still exposes `events` instead of `messages`, and because `answer` chunks are not yet concatenated into one assistant message.

- [ ] **Step 3: Add shared chat message types**

Extend `src/types/chat.ts` so the workspace store and timeline can share a single message model:

```ts
export type ChatEventType = 'thought' | 'action' | 'observation' | 'answer' | 'done' | 'error';

export interface ChatStreamEvent {
  event: ChatEventType;
  data: string;
}

export interface ChatRequest {
  sessionId: string;
  message: string;
}

export type ChatMessageRole = 'user' | 'assistant';
export type AssistantMessageStatus = 'streaming' | 'done' | 'error';
export type ProcessEntryType = Extract<ChatEventType, 'thought' | 'action' | 'observation'>;

export interface ProcessEntry {
  id: string;
  type: ProcessEntryType;
  content: string;
}

export interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  content: string;
  status: AssistantMessageStatus;
  processEntries: ProcessEntry[];
  processExpanded: boolean;
}
```

- [ ] **Step 4: Implement message-based streaming in the workspace store**

Replace the event-log logic in `src/stores/workspace.ts` with assistant-draft mutation helpers:

```ts
import { defineStore } from 'pinia';

import { streamChat } from '@/api/chat';
import { createTicket } from '@/api/tickets';
import type { ChatMessage, ChatStreamEvent, ProcessEntryType } from '@/types/chat';
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

function createUserMessage(content: string): ChatMessage {
  return {
    id: crypto.randomUUID(),
    role: 'user',
    content,
    status: 'done',
    processEntries: [],
    processExpanded: false,
  };
}

function createAssistantMessage(): ChatMessage {
  return {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '',
    status: 'streaming',
    processEntries: [],
    processExpanded: false,
  };
}

function appendProcessEntry(message: ChatMessage, type: ProcessEntryType, content: string) {
  message.processEntries.push({
    id: crypto.randomUUID(),
    type,
    content,
  });
}

function finalizeAssistantMessage(message: ChatMessage) {
  if (!message.content.trim()) {
    message.content = '本次处理没有返回最终回复，请展开过程查看详情。';
  }
  message.status = 'done';
}

function failAssistantMessage(message: ChatMessage, errorContent: string) {
  if (!message.content.trim()) {
    message.content = errorContent || '回复生成失败，请稍后重试。';
  }
  message.status = 'error';
  message.processExpanded = true;
}

function applyChatEvent(session: SessionState, message: ChatMessage, event: ChatStreamEvent) {
  if (event.event === 'answer') {
    message.content += event.data;
  } else if (event.event === 'done') {
    finalizeAssistantMessage(message);
  } else if (event.event === 'error') {
    failAssistantMessage(message, event.data);
  } else {
    appendProcessEntry(message, event.event, event.data);
  }

  const matchedOrderNo = extractOrderNo(event.data);
  if (matchedOrderNo) {
    session.context.ticketOrderNo = matchedOrderNo;
    session.context.activeTab = 'ticket';
  }
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
      const target = this.activeSession.messages.find((message) => message.id === messageId);
      if (!target || target.role !== 'assistant' || target.processEntries.length === 0) {
        return;
      }
      target.processExpanded = !target.processExpanded;
    },
    async sendMessage(message: string) {
      this.bootstrap();
      this.sending = true;
      const session = this.activeSession;
      const userMessage = createUserMessage(message);
      const assistantMessage = createAssistantMessage();

      session.messages.push(userMessage, assistantMessage);

      try {
        await streamChat(
          {
            sessionId: session.id,
            message,
          },
          (event) => {
            applyChatEvent(session, assistantMessage, event);
          }
        );

        if (assistantMessage.status === 'streaming') {
          finalizeAssistantMessage(assistantMessage);
        }
      } catch (error) {
        failAssistantMessage(
          assistantMessage,
          error instanceof Error ? error.message : '回复生成失败，请稍后重试。'
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
```

- [ ] **Step 5: Run the store tests and verify they pass**

Run:

```bash
npm test -- src/stores/workspace.spec.ts
```

Expected:

```text
PASS  src/stores/workspace.spec.ts
3 passed
```

- [ ] **Step 6: Commit the store refactor**

Run:

```bash
git add src/types/chat.ts src/stores/workspace.ts src/stores/workspace.spec.ts
git commit -m "refactor: group streamed chat events into messages"
```

### Task 2: Build And Test The Chat Timeline UI

**Files:**
- Create: `src/components/workspace/ChatTimeline.spec.ts`
- Modify: `src/components/workspace/ChatTimeline.vue`
- Modify: `src/views/WorkspaceView.vue`

- [ ] **Step 1: Write the failing component tests for disclosure rendering**

Create `src/components/workspace/ChatTimeline.spec.ts` with message-driven rendering assertions:

```ts
import { fireEvent, render, screen } from '@testing-library/vue';
import { describe, expect, it, vi } from 'vitest';

import ChatTimeline from './ChatTimeline.vue';

describe('ChatTimeline', () => {
  it('renders user and assistant messages as primary bubbles', () => {
    render(ChatTimeline, {
      props: {
        messages: [
          {
            id: 'user-1',
            role: 'user',
            content: '客户反馈物流延迟，请帮我跟进。',
            status: 'done',
            processEntries: [],
            processExpanded: false,
          },
          {
            id: 'assistant-1',
            role: 'assistant',
            content: '当前工单 WO20260409001 已进入处理流。',
            status: 'done',
            processEntries: [],
            processExpanded: false,
          },
        ],
      },
    });

    expect(screen.getByText('客户反馈物流延迟，请帮我跟进。')).toBeInTheDocument();
    expect(screen.getByText('当前工单 WO20260409001 已进入处理流。')).toBeInTheDocument();
  });

  it('renders a collapsed process disclosure and expands after toggle', async () => {
    const onToggleProcess = vi.fn();

    const { rerender } = render(ChatTimeline, {
      props: {
        messages: [
          {
            id: 'assistant-1',
            role: 'assistant',
            content: '当前工单 WO20260409001 已进入处理流。',
            status: 'done',
            processEntries: [
              { id: 'thought-1', type: 'thought', content: '需要先确认物流状态' },
              { id: 'action-1', type: 'action', content: '调用物流查询工具' },
            ],
            processExpanded: false,
          },
        ],
        onToggleProcess,
      },
    });

    expect(screen.getByRole('button', { name: '查看过程 (2)' })).toBeInTheDocument();
    expect(screen.queryByText('需要先确认物流状态')).not.toBeInTheDocument();

    await fireEvent.click(screen.getByRole('button', { name: '查看过程 (2)' }));
    expect(onToggleProcess).toHaveBeenCalledWith('assistant-1');

    await rerender({
      messages: [
        {
          id: 'assistant-1',
          role: 'assistant',
          content: '当前工单 WO20260409001 已进入处理流。',
          status: 'done',
          processEntries: [
            { id: 'thought-1', type: 'thought', content: '需要先确认物流状态' },
            { id: 'action-1', type: 'action', content: '调用物流查询工具' },
          ],
          processExpanded: true,
        },
      ],
      onToggleProcess,
    });

    expect(screen.getByText('需要先确认物流状态')).toBeInTheDocument();
    expect(screen.getByText('调用物流查询工具')).toBeInTheDocument();
  });

  it('keeps process details visible for assistant errors', () => {
    render(ChatTimeline, {
      props: {
        messages: [
          {
            id: 'assistant-1',
            role: 'assistant',
            content: '回复生成失败，请稍后重试。',
            status: 'error',
            processEntries: [
              { id: 'thought-1', type: 'thought', content: '需要查询订单状态' },
            ],
            processExpanded: true,
          },
        ],
      },
    });

    expect(screen.getByText('回复生成失败，请稍后重试。')).toBeInTheDocument();
    expect(screen.getByText('需要查询订单状态')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the component test file and verify it fails**

Run:

```bash
npm test -- src/components/workspace/ChatTimeline.spec.ts
```

Expected:

Expected: the file fails because `ChatTimeline` still expects `events`, and because no process disclosure button exists yet.

- [ ] **Step 3: Replace the event log UI with message bubbles and disclosure controls**

Update `src/components/workspace/ChatTimeline.vue` to render messages, emit process toggles, and auto-scroll only when the reader is near the bottom:

```vue
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';

import type { ChatMessage } from '@/types/chat';

const props = defineProps<{
  messages: ChatMessage[];
}>();

const emit = defineEmits<{
  toggleProcess: [messageId: string];
}>();

const viewport = ref<HTMLElement | null>(null);
const shouldStickToBottom = ref(true);

const messageSignature = computed(() =>
  props.messages.map((message) => ({
    id: message.id,
    content: message.content,
    status: message.status,
    processEntries: message.processEntries.length,
    processExpanded: message.processExpanded,
  }))
);

function handleScroll() {
  const element = viewport.value;
  if (!element) {
    return;
  }

  const distanceFromBottom = element.scrollHeight - element.scrollTop - element.clientHeight;
  shouldStickToBottom.value = distanceFromBottom < 48;
}

async function syncScroll() {
  if (!shouldStickToBottom.value) {
    return;
  }

  await nextTick();
  const element = viewport.value;
  if (!element) {
    return;
  }

  element.scrollTop = element.scrollHeight;
}

watch(messageSignature, () => {
  void syncScroll();
}, { deep: true });

onMounted(() => {
  void syncScroll();
});
</script>

<template>
  <div
    ref="viewport"
    class="chat-timeline"
    role="log"
    aria-live="polite"
    @scroll="handleScroll"
  >
    <p v-if="messages.length === 0" class="chat-timeline__empty">
      等待第一条消息开始对话。
    </p>

    <article
      v-for="message in messages"
      :key="message.id"
      class="chat-message"
      :data-role="message.role"
      :data-status="message.status"
    >
      <p class="chat-message__eyebrow">
        {{ message.role === 'user' ? '你' : '助手' }}
      </p>
      <p class="chat-message__content">{{ message.content }}</p>

      <div v-if="message.role === 'assistant'" class="chat-message__meta">
        <span v-if="message.status === 'streaming'" class="chat-message__status">生成中</span>
        <button
          v-if="message.processEntries.length > 0"
          class="chat-message__toggle"
          type="button"
          :aria-expanded="message.processExpanded"
          @click="emit('toggleProcess', message.id)"
        >
          {{ message.processExpanded ? '隐藏过程' : `查看过程 (${message.processEntries.length})` }}
        </button>
      </div>

      <div
        v-if="message.role === 'assistant' && message.processEntries.length > 0 && message.processExpanded"
        class="chat-process"
      >
        <article
          v-for="entry in message.processEntries"
          :key="entry.id"
          class="chat-process__entry"
          :data-type="entry.type"
        >
          <p class="chat-process__type">{{ entry.type }}</p>
          <p class="chat-process__content">{{ entry.content }}</p>
        </article>
      </div>
    </article>
  </div>
</template>
```

- [ ] **Step 4: Wire the workspace view to the new message prop and disclosure event**

Update `src/views/WorkspaceView.vue`:

```vue
<template>
  <section class="workspace">
    <SessionRail
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="store.activateSession"
    />

    <div class="workspace__center">
      <header class="workspace__header">
        <div>
          <p class="page__eyebrow">智能客服</p>
          <h1>工作台</h1>
        </div>
        <p class="workspace__status" :data-busy="store.sending" role="status" aria-live="polite">
          {{ store.sending ? '正在生成回复' : '等待新指令' }}
        </p>
      </header>

      <ChatTimeline
        :messages="store.activeSession.messages"
        @toggle-process="store.toggleProcess"
      />
      <ComposerBar :busy="store.sending" @submit="store.sendMessage" />
    </div>

    <ContextPanel :context="store.activeSession.context" @create="store.createTicketFromContext" />
  </section>
</template>
```

- [ ] **Step 5: Run the new component tests and verify they pass**

Run:

```bash
npm test -- src/components/workspace/ChatTimeline.spec.ts
```

Expected:

```text
PASS  src/components/workspace/ChatTimeline.spec.ts
3 passed
```

- [ ] **Step 6: Commit the timeline refactor**

Run:

```bash
git add src/components/workspace/ChatTimeline.vue src/components/workspace/ChatTimeline.spec.ts src/views/WorkspaceView.vue
git commit -m "feat: render workspace chat as streaming message bubbles"
```

### Task 3: Restyle The Conversation Surface And Composer

**Files:**
- Modify: `src/components/workspace/ComposerBar.vue`
- Create: `src/components/workspace/ComposerBar.spec.ts`
- Modify: `src/styles/theme.css`

- [ ] **Step 1: Write a small UI assertion around the updated composer copy**

Create `src/components/workspace/ComposerBar.spec.ts`:

```ts
import { render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';

import ComposerBar from './ComposerBar.vue';

describe('ComposerBar', () => {
  it('shows the streaming helper text when idle', () => {
    render(ComposerBar, {
      props: {
        busy: false,
      },
    });

    expect(screen.getByText('发送后将以流式方式持续返回结果')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '发送' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the relevant UI spec and verify it fails**

```bash
npm test -- src/components/workspace/ComposerBar.spec.ts
```

Expected: the file fails because the helper text `发送后将以流式方式持续返回结果` is not rendered yet.

- [ ] **Step 3: Restructure the composer markup for a quieter chat layout**

Update `src/components/workspace/ComposerBar.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{ submit: [message: string] }>();

const value = ref('');

function submit() {
  const message = value.value.trim();
  if (!message || props.busy) {
    return;
  }

  emit('submit', message);
  value.value = '';
}
</script>

<template>
  <form class="composer" @submit.prevent="submit">
    <label class="composer__field">
      <span class="composer__label">输入客户诉求</span>
      <textarea
        v-model="value"
        class="composer__input"
        rows="3"
        placeholder="输入客户诉求或问题..."
      />
    </label>

    <div class="composer__footer">
      <p class="composer__hint">
        {{ busy ? '正在整理回复，请稍候…' : '发送后将以流式方式持续返回结果' }}
      </p>
      <button class="composer__button" :disabled="busy" type="submit">
        {{ busy ? '生成中…' : '发送' }}
      </button>
    </div>
  </form>
</template>
```

- [ ] **Step 4: Replace the event-log styles with message bubble, process, and composer styles**

Update the workspace chat section in `src/styles/theme.css` with the following rules, replacing the current `.chat-timeline__event*` block and extending the composer block:

```css
.chat-timeline {
  display: grid;
  align-content: start;
  gap: 16px;
  min-height: 0;
  overflow: auto;
  padding: 8px 0 12px;
}

.chat-timeline__empty {
  color: var(--muted);
  padding: 28px 4px;
}

.chat-message {
  display: grid;
  gap: 10px;
  max-width: min(82%, 760px);
  padding: 18px 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 24px;
  background: rgba(11, 20, 31, 0.82);
  overflow-wrap: anywhere;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.18);
}

.chat-message[data-role='user'] {
  justify-self: end;
  background: rgba(240, 170, 82, 0.1);
  border-color: rgba(240, 170, 82, 0.18);
}

.chat-message[data-role='assistant'] {
  justify-self: start;
}

.chat-message[data-status='error'] {
  border-color: rgba(240, 120, 99, 0.26);
  background: rgba(240, 120, 99, 0.08);
}

.chat-message__eyebrow {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.chat-message__content {
  white-space: pre-wrap;
  line-height: 1.75;
}

.chat-message__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
  flex-wrap: wrap;
}

.chat-message__status {
  color: #f7d19f;
  font-size: 13px;
}

.chat-message__toggle {
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.03);
}

.chat-process {
  display: grid;
  gap: 10px;
  padding-top: 4px;
}

.chat-process__entry {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
}

.chat-process__type {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.chat-process__content {
  white-space: pre-wrap;
  line-height: 1.6;
}

.composer {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(12, 22, 34, 0.86), rgba(7, 17, 27, 0.94));
}

.composer__label {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.composer__input {
  width: 100%;
  min-height: 112px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 16px 18px;
  color: var(--text);
  background: rgba(7, 17, 27, 0.74);
  resize: vertical;
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.composer__hint {
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 980px) {
  .chat-message {
    max-width: 100%;
  }
}
```

- [ ] **Step 5: Run the unit test suite and verify chat UI changes did not break existing tests**

Run:

```bash
npm test
```

Expected:

Expected: all Vitest suites pass, including `src/stores/workspace.spec.ts`, `src/components/workspace/ChatTimeline.spec.ts`, `src/components/workspace/ComposerBar.spec.ts`, and the existing shell, ticket, and knowledge specs.

- [ ] **Step 6: Commit the chat surface polish**

Run:

```bash
git add src/components/workspace/ComposerBar.vue src/components/workspace/ComposerBar.spec.ts src/styles/theme.css
git commit -m "style: align workspace chat UI with streaming conversation layout"
```

### Task 4: Update The Console End-To-End Flow

**Files:**
- Modify: `tests/e2e/console.spec.ts`

- [ ] **Step 1: Write the failing e2e assertions for the new disclosure flow**

Adjust the mocked stream in `tests/e2e/console.spec.ts` so `answer` arrives in multiple chunks, then add assertions for the user bubble and process disclosure:

```ts
if (method === 'POST' && url.pathname === '/api/chat/stream') {
  await route.fulfill({
    status: 200,
    contentType: 'text/event-stream; charset=utf-8',
    body: [
      'event: thought\ndata: 已接收客户诉求\n\n',
      'event: action\ndata: 已创建工单 WO20260409001\n\n',
      'event: answer\ndata: 当前工单 WO20260409001 \n\n',
      'event: answer\ndata: 已进入处理流\n\n',
      'event: done\ndata: 流程结束\n\n',
    ].join(''),
  });
  return;
}
```

Update the main desktop assertions:

```ts
await page.getByLabel('输入客户诉求').fill('客户反馈物流延迟，请帮我跟进。');
await page.getByRole('button', { name: '发送' }).click();

await expect(page.getByText('客户反馈物流延迟，请帮我跟进。')).toBeVisible();
await expect(page.getByText('当前工单 WO20260409001 已进入处理流')).toBeVisible();
await expect(page.getByRole('button', { name: '查看过程 (2)' })).toBeVisible();

await page.getByRole('button', { name: '查看过程 (2)' }).click();
await expect(page.getByText('已接收客户诉求')).toBeVisible();
await expect(page.getByText('已创建工单 WO20260409001')).toBeVisible();
await expect(page.getByText('当前工单：WO20260409001')).toBeVisible();
```

- [ ] **Step 2: Run the e2e spec and verify it fails before implementation is complete**

Run:

```bash
npm run test:e2e -- tests/e2e/console.spec.ts
```

Expected:

Expected: the spec fails because the disclosure button `查看过程 (2)` is not rendered yet and process items are still shown as top-level timeline entries.

- [ ] **Step 3: Re-run the e2e spec after the UI refactor and confirm it passes**

Run:

```bash
npm run test:e2e -- tests/e2e/console.spec.ts
```

Expected:

```text
PASS  tests/e2e/console.spec.ts
2 passed
```

- [ ] **Step 4: Commit the verification updates**

Run:

```bash
git add tests/e2e/console.spec.ts
git commit -m "test: cover streamed workspace chat disclosure flow"
```

### Task 5: Final Verification And Cleanup

**Files:**
- Modify: `src/types/chat.ts`
- Modify: `src/stores/workspace.ts`
- Modify: `src/stores/workspace.spec.ts`
- Modify: `src/components/workspace/ChatTimeline.vue`
- Modify: `src/components/workspace/ChatTimeline.spec.ts`
- Modify: `src/components/workspace/ComposerBar.vue`
- Create: `src/components/workspace/ComposerBar.spec.ts`
- Modify: `src/views/WorkspaceView.vue`
- Modify: `src/styles/theme.css`
- Modify: `tests/e2e/console.spec.ts`

- [ ] **Step 1: Run the production build**

Run:

```bash
npm run build
```

Expected: the Vite production build completes successfully without Vue TypeScript errors.

- [ ] **Step 2: Run the full verification stack**

Run:

```bash
npm test
npm run test:e2e -- tests/e2e/console.spec.ts
```

Expected: all unit tests pass and `tests/e2e/console.spec.ts` passes in Playwright.

- [ ] **Step 3: Confirm the worktree is clean and the expected commits exist**

Run:

```bash
git status --short
git log --oneline -4
```

Expected:

```text
[no output from git status --short]
test: cover streamed workspace chat disclosure flow
style: align workspace chat UI with streaming conversation layout
feat: render workspace chat as streaming message bubbles
refactor: group streamed chat events into messages
```

- [ ] **Step 4: Stop with a clean workspace and hand off the verification results**

Run:

```bash
git status --short
```

Expected: no additional commit is required because each implementation task has already been committed and the worktree is clean after verification.
