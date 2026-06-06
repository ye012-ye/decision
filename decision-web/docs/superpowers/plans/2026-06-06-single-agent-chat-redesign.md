# Single-Agent Glass Chat Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `decision-web` into a single-page, single-assistant chat app with a glassmorphism + aurora-gradient visual direction, deleting the knowledge-base and work-order feature lines.

**Architecture:** Vue 3 `<script setup>` + Pinia (Options stores) + Naive UI. A `ChatLayout` (rail + chat pane, behind a fixed `BackgroundAurora`) replaces the old `AppShell`/`TopBar`/`SidebarNav`/3-column workspace. The SSE pipeline (`api/chat.ts` → `utils/sse.ts` → `stores/workspace.ts`) is unchanged; multi-agent routing stays server-side and is presented as one assistant. Design tokens (`styles/tokens.css`) and Naive overrides (`theme/index.ts`) gain glass/accent tokens and a violet accent, kept in sync per `decision-web/CLAUDE.md`.

**Tech Stack:** Vite 6, Vue 3.5, TypeScript, Pinia 2, Naive UI 2, Vitest 2 (happy-dom), Playwright 1.

**Spec:** `docs/superpowers/specs/2026-06-06-single-agent-chat-redesign-design.md`

**Conventions for every commit:** branch is `feature/single-agent-chat-redesign` (already created). Use `rtk` prefix on shell commands. Commit messages follow `<type>: <desc>`; no attribution footer (disabled globally). After each task the build (`rtk npm run build`) and unit tests (`rtk npm run test`) must be green.

---

## File Structure (decisions locked here)

**New files**
- `src/styles/glass.css` — reusable `.glass` / `.glass--strong` / `.accent-text` utilities (tokens live in `tokens.css`).
- `src/layouts/BackgroundAurora.vue` — fixed decorative gradient + drifting blobs.
- `src/layouts/ChatLayout.vue` — the single page: aurora + rail + chat pane (absorbs old `AppShell` mobile-drawer + `WorkspaceView` store wiring).
- `src/layouts/ChatLayout.spec.ts` — integration test (migrated from `WorkspaceView.spec.ts`).
- `src/components/workspace/SessionRail.spec.ts` — light unit test for the restyled rail.

**Modified**
- `src/styles/tokens.css`, `src/theme/index.ts` — accent + glass tokens, violet primary (kept in sync).
- `src/styles/layout.css` — drop app-shell/workspace classes; keep `.page` for 404; add global reduced-motion.
- `src/main.ts` — import `glass.css`.
- `src/App.vue`, `src/router/index.ts` — route `/` → `ChatLayout`, keep 404.
- `src/stores/workspace.ts` (+ `workspace.spec.ts`) — session lifecycle; strip ticket coupling.
- `src/components/workspace/SessionRail.vue`, `ChatTimeline.vue`, `ChatMessage.vue`, `ChatProcessTrace.vue`, `ToolCallCard.vue`, `ComposerBar.vue`, `src/components/common/EmptyState.vue` — glass restyle (test-ids preserved).
- `src/utils/sse.spec.ts` — drop the `uploadDocument` case (knowledge API deleted).
- `tests/e2e/{smoke,theme-toggle,console}.spec.ts` — rewrite for the single page.

**Deleted**
- `src/layouts/AppShell.vue`, `AppShell.spec.ts`, `src/layouts/TopBar.vue`
- `src/components/common/SidebarNav.vue`, `src/components/workspace/ContextPanel.vue`
- `src/views/WorkspaceView.vue`, `WorkspaceView.spec.ts`, `src/views/KnowledgeView.vue`, `src/views/TicketsView.vue`
- `src/components/knowledge/*` (3), `src/components/tickets/*` (3)
- `src/stores/knowledge.ts`, `knowledge.spec.ts`, `src/stores/tickets.ts`, `tickets.spec.ts`
- `src/api/knowledge.ts`, `src/api/tickets.ts`, `src/types/knowledge.ts`, `src/types/tickets.ts`, `src/utils/extractors.ts`

**Kept untouched:** `api/http.ts`, `api/chat.ts`, `utils/sse.ts`, `utils/markdown.ts`, `types/api.ts`, `types/chat.ts`, `stores/theme.ts`, `providers/*`, `views/NotFoundView.vue`, `theme/icons.ts`, `styles/reset.css`.

---

## Task 1: Glass design system (tokens + Naive overrides + utilities)

**Files:**
- Modify: `src/styles/tokens.css`
- Modify: `src/theme/index.ts`
- Create: `src/styles/glass.css`
- Modify: `src/main.ts:6-8`

- [ ] **Step 1: Update the light `:root` primary trio + add accent/glass tokens in `tokens.css`**

Replace the light primary block (currently `--color-primary: #2563eb;` … `--color-primary-soft-strong: rgba(37, 99, 235, 0.16);`) with:

```css
  --color-primary: #6d5efc;
  --color-primary-hover: #5a49e6;
  --color-primary-pressed: #4836c9;
  --color-primary-soft: rgba(109, 94, 252, 0.10);
  --color-primary-soft-strong: rgba(109, 94, 252, 0.16);
```

Then, inside the light `:root { … }` (before the closing `}` of `:root`, after the `--font-mono` line), add:

```css

  /* Accent & gradient */
  --color-accent: #7c5cff;
  --color-accent-2: #22d3ee;
  --accent-gradient: linear-gradient(135deg, #7c5cff 0%, #4f7cff 50%, #22d3ee 100%);
  --app-bg-gradient: linear-gradient(180deg, #eef0ff 0%, #f6f7fc 45%, #fdeef4 100%);
  --aurora-1: rgba(124, 92, 255, 0.40);
  --aurora-2: rgba(34, 211, 238, 0.34);
  --aurora-3: rgba(236, 72, 153, 0.30);

  /* Glass */
  --glass-bg: rgba(255, 255, 255, 0.55);
  --glass-bg-strong: rgba(255, 255, 255, 0.72);
  --glass-border: rgba(255, 255, 255, 0.65);
  --glass-blur: 18px;
  --glass-shadow: 0 8px 32px rgba(31, 41, 55, 0.12);
```

- [ ] **Step 2: Update the dark theme primary trio + add accent/glass tokens in `tokens.css`**

Replace the dark primary block (`--color-primary: #f0aa52;` … `--color-primary-soft-strong: rgba(240, 170, 82, 0.22);`) with:

```css
  --color-primary: #a78bfa;
  --color-primary-hover: #b9a3ff;
  --color-primary-pressed: #9374f2;
  --color-primary-soft: rgba(167, 139, 250, 0.14);
  --color-primary-soft-strong: rgba(167, 139, 250, 0.24);
```

Then inside `html[data-theme='dark'] { … }` (before its closing `}`, after the `--shadow-lg` line) add:

```css

  --color-accent: #a78bfa;
  --color-accent-2: #22d3ee;
  --accent-gradient: linear-gradient(135deg, #a78bfa 0%, #6366f1 50%, #22d3ee 100%);
  --app-bg-gradient: radial-gradient(120% 120% at 50% -10%, #16203a 0%, #0b0f17 55%, #090c12 100%);
  --aurora-1: rgba(124, 92, 255, 0.32);
  --aurora-2: rgba(34, 211, 238, 0.24);
  --aurora-3: rgba(236, 72, 153, 0.22);

  --glass-bg: rgba(20, 26, 36, 0.55);
  --glass-bg-strong: rgba(20, 26, 36, 0.74);
  --glass-border: rgba(255, 255, 255, 0.12);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
```

- [ ] **Step 3: Make `body` use the gradient (fallback behind the aurora) in `tokens.css`**

In the `body { … }` rule, change `background: var(--color-bg);` to:

```css
  background: var(--app-bg-gradient);
  background-attachment: fixed;
```

- [ ] **Step 4: Sync the violet primary into Naive overrides (`src/theme/index.ts`)**

In the `light` const, set:

```ts
  primary: '#6d5efc',
  primaryHover: '#5a49e6',
  primaryPressed: '#4836c9',
```

In the `dark` const, set:

```ts
  primary: '#a78bfa',
  primaryHover: '#b9a3ff',
  primaryPressed: '#9374f2',
```

Leave success/warning/danger/text/border/surface untouched.

- [ ] **Step 5: Create `src/styles/glass.css`**

```css
/* Reusable glassmorphism utilities. Color/blur tokens live in tokens.css. */
.glass {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(140%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(140%);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
}

.glass--strong {
  background: var(--glass-bg-strong);
}

.accent-text {
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
```

- [ ] **Step 6: Import `glass.css` in `src/main.ts`**

Change the style imports (lines 6-8) to:

```ts
import './styles/reset.css';
import './styles/tokens.css';
import './styles/glass.css';
import './styles/layout.css';
```

- [ ] **Step 7: Verify build + tests still green**

Run: `rtk npm run build`
Expected: `vue-tsc` passes, `vite build` succeeds (no type errors).

Run: `rtk npm run test`
Expected: all existing specs PASS (this task is additive; `theme.spec` asserts logic, not colors).

- [ ] **Step 8: Commit**

```bash
rtk git add src/styles/tokens.css src/theme/index.ts src/styles/glass.css src/main.ts
rtk git commit -m "feat: add glass + aurora design tokens and violet accent"
```

---

## Task 2: Workspace store — add session lifecycle (additive, TDD)

Add `newConversation` / `removeSession` and first-message-becomes-title **without** removing the ticket code yet (old consumers must keep compiling until Task 4).

**Files:**
- Modify: `src/stores/workspace.ts`
- Test: `src/stores/workspace.spec.ts`

- [ ] **Step 1: Write failing tests — append to `workspace.spec.ts`**

Add these three `it` blocks inside the `describe('workspace store', …)` block (before its closing `});`):

```ts
  it('newConversation creates a fresh active session at the front', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    const firstId = store.activeSessionId;

    store.newConversation();

    expect(store.sessions.length).toBe(2);
    expect(store.sessions[0].id).toBe(store.activeSessionId);
    expect(store.activeSessionId).not.toBe(firstId);
    expect(store.activeSession.messages).toHaveLength(0);
  });

  it('removeSession drops a session and keeps at least one', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    store.newConversation();
    const activeId = store.activeSessionId;

    store.removeSession(activeId);
    expect(store.sessions.some((s) => s.id === activeId)).toBe(false);
    expect(store.sessions.length).toBe(1);

    const lastRemaining = store.sessions[0].id;
    store.removeSession(lastRemaining);
    expect(store.sessions.length).toBe(1);
    expect(store.activeSessionId).toBe(store.sessions[0].id);
  });

  it('first user message becomes the session title', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('帮我查询最近的订单状态并跟进');
    expect(store.activeSession.title).toBe('帮我查询最近的订单状态并跟进'.slice(0, 20));
  });
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `rtk npx vitest run src/stores/workspace.spec.ts`
Expected: the 3 new tests FAIL (`newConversation`/`removeSession` not a function; title still `新会话`).

- [ ] **Step 3: Implement in `workspace.ts`**

Add a title constant near the other constants (after `function createSession(...) { … }`):

```ts
const DEFAULT_SESSION_TITLE = '新会话';
const TITLE_MAX_LEN = 20;
```

Add two actions immediately after the existing `activateSession` action:

```ts
    newConversation() {
      const session = createSession(DEFAULT_SESSION_TITLE);
      this.sessions.unshift(session);
      this.activeSessionId = session.id;
    },
    removeSession(sessionId: string) {
      const index = this.sessions.findIndex((session) => session.id === sessionId);
      if (index === -1) return;

      this.sessions.splice(index, 1);
      if (this.sessions.length === 0) {
        this.sessions.push(createSession(DEFAULT_SESSION_TITLE));
      }
      if (this.activeSessionId === sessionId) {
        const nextIndex = Math.min(index, this.sessions.length - 1);
        this.activeSessionId = this.sessions[nextIndex].id;
      }
    },
```

In `sendMessage`, right after `const session = this.activeSession;`, add the title assignment:

```ts
      if (session.messages.length === 0) {
        session.title = message.trim().slice(0, TITLE_MAX_LEN) || DEFAULT_SESSION_TITLE;
      }
```

- [ ] **Step 4: Run tests, verify they pass**

Run: `rtk npx vitest run src/stores/workspace.spec.ts`
Expected: all tests PASS (new + pre-existing ticket tests still green — `createSession` still produces `context`).

- [ ] **Step 5: Commit**

```bash
rtk git add src/stores/workspace.ts src/stores/workspace.spec.ts
rtk git commit -m "feat: add session lifecycle and auto-title to workspace store"
```

---

## Task 3: BackgroundAurora + ChatLayout (additive, not yet routed)

Build the new layout but keep the old route wired so the app stays runnable.

**Files:**
- Create: `src/layouts/BackgroundAurora.vue`
- Create: `src/layouts/ChatLayout.vue`
- Create: `src/layouts/ChatLayout.spec.ts`

- [ ] **Step 1: Create `src/layouts/BackgroundAurora.vue`**

```vue
<script setup lang="ts">
// Fixed, decorative gradient + drifting aurora blobs behind the app.
</script>

<template>
  <div class="aurora" aria-hidden="true">
    <span class="aurora__blob aurora__blob--1" />
    <span class="aurora__blob aurora__blob--2" />
    <span class="aurora__blob aurora__blob--3" />
  </div>
</template>

<style scoped>
.aurora {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background: var(--app-bg-gradient);
  pointer-events: none;
}
.aurora__blob {
  position: absolute;
  width: 46vmax;
  height: 46vmax;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.9;
  will-change: transform;
}
.aurora__blob--1 {
  top: -12vmax;
  left: -8vmax;
  background: radial-gradient(circle at 30% 30%, var(--aurora-1), transparent 70%);
  animation: aurora-drift-1 24s ease-in-out infinite alternate;
}
.aurora__blob--2 {
  top: 18vmax;
  right: -14vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-2), transparent 70%);
  animation: aurora-drift-2 30s ease-in-out infinite alternate;
}
.aurora__blob--3 {
  bottom: -16vmax;
  left: 28vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-3), transparent 70%);
  animation: aurora-drift-3 28s ease-in-out infinite alternate;
}
@keyframes aurora-drift-1 { to { transform: translate3d(8vmax, 6vmax, 0) scale(1.15); } }
@keyframes aurora-drift-2 { to { transform: translate3d(-10vmax, 4vmax, 0) scale(1.1); } }
@keyframes aurora-drift-3 { to { transform: translate3d(6vmax, -8vmax, 0) scale(1.2); } }
</style>
```

- [ ] **Step 2: Create `src/layouts/ChatLayout.vue`**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { NButton, NDrawer, NDrawerContent, NIcon, NTag } from 'naive-ui';

import BackgroundAurora from './BackgroundAurora.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import { MenuIcon } from '@/theme/icons';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();

const drawerOpen = ref(false);
const isMobile = ref(false);

function evaluateViewport() {
  isMobile.value = window.matchMedia('(max-width: 980px)').matches;
}

onMounted(() => {
  store.bootstrap();
  evaluateViewport();
  window.addEventListener('resize', evaluateViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', evaluateViewport);
});

const railVisible = computed(() => !isMobile.value);

function onSelect(id: string) {
  store.activateSession(id);
  drawerOpen.value = false;
}
function onCreate() {
  store.newConversation();
  drawerOpen.value = false;
}
function onDelete(id: string) {
  store.removeSession(id);
}
</script>

<template>
  <div class="chat-layout">
    <BackgroundAurora />

    <SessionRail
      v-if="railVisible"
      class="chat-layout__rail"
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="onSelect"
      @create="onCreate"
      @delete="onDelete"
    />
    <NDrawer v-else v-model:show="drawerOpen" :width="288" placement="left">
      <NDrawerContent title="会话" closable>
        <SessionRail
          :sessions="store.sessions"
          :active-session-id="store.activeSessionId"
          @select="onSelect"
          @create="onCreate"
          @delete="onDelete"
        />
      </NDrawerContent>
    </NDrawer>

    <section class="chat-layout__pane">
      <header class="chat-pane__header glass">
        <NButton
          v-if="isMobile"
          class="chat-pane__menu"
          quaternary
          circle
          aria-label="打开会话"
          @click="drawerOpen = true"
        >
          <template #icon><NIcon :component="MenuIcon" /></template>
        </NButton>
        <h1 class="chat-pane__title">{{ store.activeSession.title }}</h1>
        <NTag :type="store.sending ? 'warning' : 'success'" :bordered="false" round size="small">
          {{ store.sending ? '正在生成' : '在线' }}
        </NTag>
      </header>

      <ChatTimeline :messages="store.activeSession.messages" @suggest="store.sendMessage" />

      <ComposerBar :busy="store.sending" @submit="store.sendMessage" @stop="store.stopStreaming" />
    </section>
  </div>
</template>

<style scoped>
.chat-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr);
  gap: var(--space-4);
  height: 100vh;
  padding: var(--space-4);
}
.chat-layout__pane {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  min-width: 0;
  min-height: 0;
}
.chat-pane__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl);
}
.chat-pane__title {
  flex: 1 1 auto;
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 980px) {
  .chat-layout {
    grid-template-columns: 1fr;
    padding: var(--space-2);
  }
}
</style>
```

- [ ] **Step 3: Create `src/layouts/ChatLayout.spec.ts`**

```ts
import { fireEvent, render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatStreamEvent } from '@/types/chat';
import ChatLayout from './ChatLayout.vue';

let streamScenario: (onEvent: (event: ChatStreamEvent) => void) => Promise<void> = async () => {};

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => streamScenario(onEvent)),
}));

describe('ChatLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    streamScenario = async (onEvent) => {
      onEvent({ event: 'thought', data: '已接收客户诉求' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '已经' });
      await Promise.resolve();
      onEvent({ event: 'answer', data: '处理完成' });
      onEvent({ event: 'done', data: '[DONE]' });
    };
  });

  it('renders streamed assistant content and the process trace', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    render(ChatLayout, { global: { plugins: [pinia] } });

    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '帮我处理一下');
    await fireEvent.click(screen.getByTestId('composer-submit'));

    expect(await screen.findByText('帮我处理一下')).toBeInTheDocument();
    expect(await screen.findByText('已经处理完成')).toBeInTheDocument();
    expect(await screen.findByTestId('chat-process-trace')).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Run the new test + build**

Run: `rtk npx vitest run src/layouts/ChatLayout.spec.ts`
Expected: PASS (desktop default: `matchMedia('(max-width: 980px)')` is false in happy-dom, so the rail renders, no drawer).

Run: `rtk npm run build`
Expected: passes (new files compile; old route still active).

- [ ] **Step 5: Commit**

```bash
rtk git add src/layouts/BackgroundAurora.vue src/layouts/ChatLayout.vue src/layouts/ChatLayout.spec.ts
rtk git commit -m "feat: add BackgroundAurora and ChatLayout single-page shell"
```

---

## Task 4: Route `/` to ChatLayout; delete old layout & ticket-panel consumers

**Files:**
- Modify: `src/router/index.ts`
- Modify: `src/App.vue`
- Delete: `src/layouts/AppShell.vue`, `src/layouts/AppShell.spec.ts`, `src/layouts/TopBar.vue`, `src/components/common/SidebarNav.vue`, `src/components/workspace/ContextPanel.vue`, `src/views/WorkspaceView.vue`, `src/views/WorkspaceView.spec.ts`

- [ ] **Step 1: Replace `src/router/index.ts`**

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import ChatLayout from '@/layouts/ChatLayout.vue';
import NotFoundView from '@/views/NotFoundView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/', component: ChatLayout, meta: { title: '智能助手' } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '未找到页面' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
```

- [ ] **Step 2: Replace `src/App.vue`**

```vue
<template>
  <AppProviders>
    <RouterView />
  </AppProviders>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';
import AppProviders from '@/providers/AppProviders.vue';
</script>
```

- [ ] **Step 3: Delete the obsolete files**

```bash
rtk git rm src/layouts/AppShell.vue src/layouts/AppShell.spec.ts src/layouts/TopBar.vue \
  src/components/common/SidebarNav.vue src/components/workspace/ContextPanel.vue \
  src/views/WorkspaceView.vue src/views/WorkspaceView.spec.ts
```

- [ ] **Step 4: Verify build + tests**

Run: `rtk npm run build`
Expected: passes. `vue-tsc` confirms nothing still imports the deleted files (the store keeps its ticket code for now, but `WorkspaceView`/`ContextPanel` — its only UI consumers — are gone).

Run: `rtk npm run test`
Expected: all remaining specs PASS (`ChatLayout.spec` covers the integration path; `AppShell.spec`/`WorkspaceView.spec` removed).

- [ ] **Step 5: Commit**

```bash
rtk git add -A
rtk git commit -m "refactor: route to single ChatLayout and remove old shell"
```

---

## Task 5: Strip ticket coupling from the workspace store

**Files:**
- Modify: `src/stores/workspace.ts` (full replacement below)
- Modify: `src/stores/workspace.spec.ts` (full replacement below)

- [ ] **Step 1: Replace `src/stores/workspace.spec.ts`**

```ts
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ChatAssistantMessage, ChatMessage, ChatStreamEvent } from '@/types/chat';
import { vi } from 'vitest';

let resumeStream: (() => void) | null = null;
let streamScenario: (onEvent: (event: ChatStreamEvent) => void) => Promise<void> = async () => {};

function createDefaultScenario() {
  return async (onEvent: (event: ChatStreamEvent) => void) => {
    onEvent({ event: 'thought', data: '需要查询物流' });
    await new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, 0);
      resumeStream = () => {
        clearTimeout(timer);
        resolve();
      };
    });
    onEvent({ event: 'action', data: 'callExternalApiTool | {"service":"logistics"}' });
    onEvent({ event: 'observation', data: '查询到物流延迟 2 天' });
    onEvent({ event: 'answer', data: '物流已更新，' });
    onEvent({ event: 'answer', data: '请稍后查收' });
    onEvent({ event: 'done', data: '[DONE]' });
  };
}

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => streamScenario(onEvent)),
}));

import { useWorkspaceStore } from './workspace';

function asAssistantMessage(message: ChatMessage | undefined): ChatAssistantMessage {
  expect(message?.role).toBe('assistant');
  return message as ChatAssistantMessage;
}

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    resumeStream = null;
    streamScenario = createDefaultScenario();
  });

  it('sending creates one user message and one assistant message per streamed turn', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.messages).toHaveLength(2);
    expect(store.activeSession.messages[0]?.role).toBe('user');
    expect(store.activeSession.messages[0]?.content).toBe('客户投诉物流慢');
    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('done');
  });

  it('multiple streamed answer payloads accumulate into one assistant message', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，请稍后查收');
  });

  it('process entries collect under that assistant message', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.process.map((entry) => entry.type)).toEqual([
      'thought',
      'action',
      'observation',
    ]);
  });

  it('session switching during streaming keeps updates on the originating session', async () => {
    const store = useWorkspaceStore();
    store.bootstrap();

    const originalSession = store.activeSession;
    store.sessions.push({ id: crypto.randomUUID(), title: '新会话 2', messages: [] });

    const sendPromise = store.sendMessage('客户投诉物流慢');
    await Promise.resolve();

    store.activateSession(store.sessions[1].id);
    resumeStream?.();
    await sendPromise;

    expect(originalSession.messages).toHaveLength(2);
    const assistantMessage = asAssistantMessage(originalSession.messages[1]);
    expect(assistantMessage.content).toBe('物流已更新，请稍后查收');
    expect(store.sessions[1].messages).toHaveLength(0);
    expect(store.activeSessionId).toBe(store.sessions[1].id);
  });

  it('marks assistant message as error with visible text when stream request throws', async () => {
    streamScenario = async () => {
      throw new Error('上游连接失败');
    };

    const store = useWorkspaceStore();
    await expect(store.sendMessage('客户投诉物流慢')).rejects.toThrow('上游连接失败');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('error');
    expect(assistantMessage.content).toContain('上游连接失败');
    expect(assistantMessage.processExpanded).toBe(true);
  });

  it('shows SSE error event text in assistant message content', async () => {
    streamScenario = async (onEvent) => {
      onEvent({ event: 'error', data: '工具调用失败: logistics timeout' });
    };

    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('error');
    expect(assistantMessage.content).toContain('工具调用失败');
    expect(assistantMessage.processExpanded).toBe(true);
  });

  it('uses fallback assistant text when stream completes without answer content', async () => {
    streamScenario = async (onEvent) => {
      onEvent({ event: 'thought', data: '正在分析问题' });
      onEvent({ event: 'done', data: '[DONE]' });
    };

    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    const assistantMessage = asAssistantMessage(store.activeSession.messages[1]);
    expect(assistantMessage.status).toBe('done');
    expect(assistantMessage.content).toBe('暂未获取到回复，请稍后重试。');
  });

  it('newConversation creates a fresh active session at the front', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    const firstId = store.activeSessionId;

    store.newConversation();

    expect(store.sessions.length).toBe(2);
    expect(store.sessions[0].id).toBe(store.activeSessionId);
    expect(store.activeSessionId).not.toBe(firstId);
    expect(store.activeSession.messages).toHaveLength(0);
  });

  it('removeSession drops a session and keeps at least one', () => {
    const store = useWorkspaceStore();
    store.bootstrap();
    store.newConversation();
    const activeId = store.activeSessionId;

    store.removeSession(activeId);
    expect(store.sessions.some((s) => s.id === activeId)).toBe(false);
    expect(store.sessions.length).toBe(1);

    const lastRemaining = store.sessions[0].id;
    store.removeSession(lastRemaining);
    expect(store.sessions.length).toBe(1);
    expect(store.activeSessionId).toBe(store.sessions[0].id);
  });

  it('first user message becomes the session title', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('帮我查询最近的订单状态并跟进');
    expect(store.activeSession.title).toBe('帮我查询最近的订单状态并跟进'.slice(0, 20));
  });
});
```

- [ ] **Step 2: Run tests, verify the ticket assertions are gone and remaining fail/pass as expected**

Run: `rtk npx vitest run src/stores/workspace.spec.ts`
Expected: the `session switching…` test FAILS (store still pushes `context` shape mismatch is fine, but store still imports `@/api/tickets` which is unmocked now → `createTicket` real import is unused, OK). Most tests PASS already; this step mainly confirms no test references `@/api/tickets`. If all pass, proceed; the point of the next step is removing dead store code.

> Note: if every test already passes here, that's expected — the spec rewrite removed the ticket-coupled tests. Step 3 removes the now-dead store code.

- [ ] **Step 3: Replace `src/stores/workspace.ts`**

```ts
import { defineStore } from 'pinia';

import { streamChat } from '@/api/chat';
import type { ChatAssistantMessage, ChatMessage, ChatProcessType } from '@/types/chat';

interface SessionState {
  id: string;
  title: string;
  messages: ChatMessage[];
}

const DEFAULT_SESSION_TITLE = '新会话';
const TITLE_MAX_LEN = 20;

function createSession(title: string): SessionState {
  return {
    id: crypto.randomUUID(),
    title,
    messages: [],
  };
}

const FALLBACK_ASSISTANT_MESSAGE = '暂未获取到回复，请稍后重试。';
const FALLBACK_ASSISTANT_ERROR_MESSAGE = '请求失败，请稍后重试。';

function appendProcessEntry(message: ChatAssistantMessage, type: ChatProcessType, content: string) {
  message.process.push({ id: crypto.randomUUID(), type, content });
}

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    sessions: [createSession(DEFAULT_SESSION_TITLE)],
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
    newConversation() {
      const session = createSession(DEFAULT_SESSION_TITLE);
      this.sessions.unshift(session);
      this.activeSessionId = session.id;
    },
    removeSession(sessionId: string) {
      const index = this.sessions.findIndex((session) => session.id === sessionId);
      if (index === -1) return;

      this.sessions.splice(index, 1);
      if (this.sessions.length === 0) {
        this.sessions.push(createSession(DEFAULT_SESSION_TITLE));
      }
      if (this.activeSessionId === sessionId) {
        const nextIndex = Math.min(index, this.sessions.length - 1);
        this.activeSessionId = this.sessions[nextIndex].id;
      }
    },
    async sendMessage(message: string) {
      this.bootstrap();
      this.abortController?.abort();
      const controller = new AbortController();
      this.abortController = controller;
      this.sending = true;
      const session = this.activeSession;

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
              } else if (event.event === 'error') {
                target.status = 'error';
                target.processExpanded = true;
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
        this.sending = false;
        this.abortController = null;
      }
    },
    stopStreaming() {
      this.abortController?.abort();
    },
  },
});
```

> This removes `createTicketFromContext`, the `context`/`WorkspaceContext` shape, `toggleProcess` (pre-existing dead code, no longer needed without the trace toggle), and the `@/api/tickets` + `@/utils/extractors` imports.

- [ ] **Step 4: Run tests + build**

Run: `rtk npx vitest run src/stores/workspace.spec.ts`
Expected: all PASS.

Run: `rtk npm run build`
Expected: passes (`api/tickets`/`extractors` still exist on disk but the store no longer imports them).

- [ ] **Step 5: Commit**

```bash
rtk git add src/stores/workspace.ts src/stores/workspace.spec.ts
rtk git commit -m "refactor: remove ticket coupling from workspace store"
```

---

## Task 6: Delete the knowledge & ticket feature lines

**Files:**
- Delete (views): `src/views/KnowledgeView.vue`, `src/views/TicketsView.vue`
- Delete (components): `src/components/knowledge/KnowledgeCreateBase.vue`, `KnowledgeDocumentTable.vue`, `KnowledgeSidebar.vue`, `src/components/tickets/TicketDetailPanel.vue`, `TicketFilters.vue`, `TicketList.vue`
- Delete (stores): `src/stores/knowledge.ts`, `knowledge.spec.ts`, `src/stores/tickets.ts`, `tickets.spec.ts`
- Delete (api/types/utils): `src/api/knowledge.ts`, `src/api/tickets.ts`, `src/types/knowledge.ts`, `src/types/tickets.ts`, `src/utils/extractors.ts`
- Modify: `src/utils/sse.spec.ts`

- [ ] **Step 1: Remove the `uploadDocument` case from `src/utils/sse.spec.ts`**

Delete the import line `import { uploadDocument } from '@/api/knowledge';` and delete the entire `it('surfaces backend message for upload failures', …)` block. Keep the `parseSseChunk` tests and the `requestJson` envelope test.

- [ ] **Step 2: Delete the feature files**

```bash
rtk git rm src/views/KnowledgeView.vue src/views/TicketsView.vue \
  src/components/knowledge/KnowledgeCreateBase.vue src/components/knowledge/KnowledgeDocumentTable.vue src/components/knowledge/KnowledgeSidebar.vue \
  src/components/tickets/TicketDetailPanel.vue src/components/tickets/TicketFilters.vue src/components/tickets/TicketList.vue \
  src/stores/knowledge.ts src/stores/knowledge.spec.ts src/stores/tickets.ts src/stores/tickets.spec.ts \
  src/api/knowledge.ts src/api/tickets.ts src/types/knowledge.ts src/types/tickets.ts src/utils/extractors.ts
```

- [ ] **Step 3: Verify build + tests (vue-tsc catches any dangling import)**

Run: `rtk npm run build`
Expected: passes — no remaining references to any deleted module.

Run: `rtk npm run test`
Expected: all PASS (`sse.spec` keeps `parseSseChunk` + `requestJson` cases).

- [ ] **Step 4: Commit**

```bash
rtk git add -A
rtk git commit -m "refactor: delete knowledge and work-order feature lines"
```

---

## Task 7: Restyle SessionRail (glass + theme toggle + working delete)

**Files:**
- Modify: `src/components/workspace/SessionRail.vue` (full replacement)
- Create: `src/components/workspace/SessionRail.spec.ts`

- [ ] **Step 1: Write the test first — `src/components/workspace/SessionRail.spec.ts`**

```ts
import { fireEvent, render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import SessionRail from './SessionRail.vue';

const sessions = [
  { id: 's1', title: '会话一', messages: [] },
  { id: 's2', title: '会话二', messages: [] },
];

describe('SessionRail', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('renders sessions and emits select', async () => {
    const view = render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    expect(screen.getByText('会话一')).toBeInTheDocument();
    await fireEvent.click(screen.getByText('会话二'));
    expect(view.emitted('select')).toEqual([['s2']]);
  });

  it('emits create when 新对话 clicked', async () => {
    const view = render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    await fireEvent.click(screen.getByText('新对话'));
    expect(view.emitted('create')).toEqual([[]]);
  });

  it('renders a theme toggle', () => {
    render(SessionRail, { props: { sessions, activeSessionId: 's1' } });
    expect(screen.getByTestId('theme-toggle')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run it, verify it fails**

Run: `rtk npx vitest run src/components/workspace/SessionRail.spec.ts`
Expected: FAIL — current rail has no `新对话` text and no `theme-toggle`.

- [ ] **Step 3: Replace `src/components/workspace/SessionRail.vue`**

```vue
<script setup lang="ts">
import { computed, h, ref } from 'vue';
import { NButton, NDropdown, NEmpty, NIcon, NInput, NScrollbar, NTooltip } from 'naive-ui';

import { AddIcon, AutoIcon, MoonIcon, MoreIcon, SearchIcon, SunIcon } from '@/theme/icons';
import { useThemeStore } from '@/stores/theme';
import type { ChatMessage } from '@/types/chat';

const props = defineProps<{
  sessions: Array<{ id: string; title: string; messages?: ChatMessage[] }>;
  activeSessionId: string;
}>();
const emit = defineEmits<{
  (e: 'select', id: string): void;
  (e: 'create'): void;
  (e: 'delete', id: string): void;
}>();

const theme = useThemeStore();
const themeIcon = computed(() =>
  theme.mode === 'light' ? SunIcon : theme.mode === 'dark' ? MoonIcon : AutoIcon
);
const themeLabel = computed(() =>
  theme.mode === 'light' ? '亮色' : theme.mode === 'dark' ? '暗色' : '跟随系统'
);
function cycleTheme() {
  const next = theme.mode === 'light' ? 'dark' : theme.mode === 'dark' ? 'auto' : 'light';
  theme.setMode(next);
}

const query = ref('');
const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.sessions;
  return props.sessions.filter((s) => s.title.toLowerCase().includes(q));
});

const dropdownOptions = [
  { key: 'delete', label: '删除会话', props: { style: 'color: var(--color-danger)' } },
];
function onDropdown(key: string, id: string) {
  if (key === 'delete') emit('delete', id);
}

function renderIcon(icon: unknown) {
  return () => h(NIcon, null, { default: () => h(icon as never) });
}
</script>

<template>
  <aside class="session-rail" data-testid="session-rail">
    <header class="session-rail__brand">
      <span class="session-rail__logo accent-text">智能助手</span>
    </header>

    <NButton
      class="session-rail__new"
      block
      strong
      :render-icon="renderIcon(AddIcon)"
      @click="emit('create')"
    >
      新对话
    </NButton>

    <NInput
      v-model:value="query"
      placeholder="搜索会话"
      size="small"
      clearable
      class="session-rail__search"
    >
      <template #prefix><NIcon :component="SearchIcon" /></template>
    </NInput>

    <NScrollbar class="session-rail__scroll">
      <NEmpty v-if="filtered.length === 0" description="暂无会话" size="small" style="margin-top: 32px;" />
      <ul class="session-rail__list">
        <li
          v-for="session in filtered"
          :key="session.id"
          class="session-rail__item"
          :data-active="session.id === activeSessionId"
          :data-testid="`session-${session.id}`"
        >
          <button type="button" class="session-rail__btn" @click="emit('select', session.id)">
            <span class="session-rail__title">{{ session.title }}</span>
            <span class="session-rail__meta">{{ session.messages?.length ?? 0 }} 条记录</span>
          </button>
          <NDropdown
            :options="dropdownOptions"
            trigger="click"
            @select="(key) => onDropdown(key, session.id)"
          >
            <NButton quaternary circle size="tiny" aria-label="会话操作">
              <template #icon><NIcon :component="MoreIcon" /></template>
            </NButton>
          </NDropdown>
        </li>
      </ul>
    </NScrollbar>

    <footer class="session-rail__footer">
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton
            quaternary
            :render-icon="renderIcon(themeIcon)"
            :aria-label="`切换主题（当前：${themeLabel}）`"
            data-testid="theme-toggle"
            @click="cycleTheme"
          >
            {{ themeLabel }}
          </NButton>
        </template>
        当前主题：{{ themeLabel }}
      </NTooltip>
    </footer>
  </aside>
</template>

<style scoped>
.session-rail {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  height: 100%;
  min-width: 0;
  padding: var(--space-4);
  border-radius: var(--radius-xl);
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(140%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(140%);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
}
.session-rail__brand {
  padding: 2px 4px 0;
}
.session-rail__logo {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.session-rail__scroll {
  flex: 1 1 auto;
  min-height: 0;
}
.session-rail__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 4px;
}
.session-rail__item {
  position: relative;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  padding: 0 4px 0 6px;
  border-radius: var(--radius-md);
  transition: background 0.2s ease;
}
.session-rail__item:hover {
  background: var(--color-surface-hover);
}
.session-rail__item[data-active='true'] {
  background: var(--color-primary-soft);
  box-shadow: inset 3px 0 0 0 var(--color-accent);
}
.session-rail__btn {
  display: grid;
  gap: 2px;
  padding: 10px 6px;
  border: 0;
  background: none;
  text-align: left;
  color: var(--color-text);
  min-width: 0;
}
.session-rail__title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-rail__meta {
  font-size: 12px;
  color: var(--color-text-muted);
}
.session-rail__footer {
  display: flex;
  padding-top: var(--space-2);
  border-top: 1px solid var(--glass-border);
}
</style>
```

- [ ] **Step 4: Run the rail test + full suite**

Run: `rtk npx vitest run src/components/workspace/SessionRail.spec.ts`
Expected: PASS.

Run: `rtk npm run test`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add src/components/workspace/SessionRail.vue src/components/workspace/SessionRail.spec.ts
rtk git commit -m "feat: glass session rail with theme toggle and delete"
```

---

## Task 8: Restyle ChatTimeline + EmptyState (centered column, glass chips)

**Files:**
- Modify: `src/components/workspace/ChatTimeline.vue` (full replacement)
- Modify: `src/components/common/EmptyState.vue` (full replacement)

- [ ] **Step 1: Replace `src/components/workspace/ChatTimeline.vue`**

```vue
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { NButton, NIcon } from 'naive-ui';

import { ArrowDownIcon } from '@/theme/icons';
import type { ChatMessage as ChatMessageType } from '@/types/chat';
import ChatMessageComp from './ChatMessage.vue';
import EmptyState from '@/components/common/EmptyState.vue';

const props = defineProps<{ messages: ChatMessageType[] }>();
const emit = defineEmits<{ 'suggest': [text: string] }>();

const timelineRef = ref<HTMLElement | null>(null);
const stickToBottom = ref(true);
const showJumpButton = ref(false);
const nearBottomThreshold = 64;
const jumpThreshold = 200;

const suggestions = ['订单 A2025 的物流状态？', '帮我总结这位客户的诉求', '生成一个退款工单草稿'];

function isNearBottom(el: HTMLElement) {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= nearBottomThreshold;
}
function handleScroll() {
  const el = timelineRef.value;
  if (!el) return;
  stickToBottom.value = isNearBottom(el);
  showJumpButton.value = el.scrollHeight - el.scrollTop - el.clientHeight > jumpThreshold;
}
function scrollToBottom(smooth = false) {
  const el = timelineRef.value;
  if (!el) return;
  el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
}

const autoScrollSignal = computed(() =>
  props.messages
    .map((m) => (m.role === 'assistant' ? `${m.id}:${m.content}:${m.process.length}` : `${m.id}:${m.content}`))
    .join('|')
);

watch(autoScrollSignal, () => {
  if (!stickToBottom.value) return;
  nextTick(() => scrollToBottom(false));
});

onMounted(() => {
  timelineRef.value?.addEventListener('scroll', handleScroll);
  scrollToBottom(false);
});
onBeforeUnmount(() => {
  timelineRef.value?.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="chat-timeline-wrapper">
    <div ref="timelineRef" class="chat-timeline" role="log" aria-live="polite" data-testid="chat-timeline">
      <div class="chat-timeline__inner">
        <EmptyState
          v-if="messages.length === 0"
          title="开始一段对话"
          description="发送一条问题，或试试下面的示例："
        >
          <div class="chat-timeline__suggestions">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              class="chat-timeline__chip glass"
              @click="emit('suggest', suggestion)"
            >
              {{ suggestion }}
            </button>
          </div>
        </EmptyState>

        <ChatMessageComp
          v-for="message in messages"
          :key="message.id"
          :message="message"
        />
      </div>
    </div>

    <NButton
      v-if="showJumpButton"
      class="chat-timeline__jump"
      circle
      type="primary"
      data-testid="chat-jump-bottom"
      @click="scrollToBottom(true)"
    >
      <template #icon><NIcon :component="ArrowDownIcon" /></template>
    </NButton>
  </div>
</template>

<style scoped>
.chat-timeline-wrapper {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}
.chat-timeline {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-4) var(--space-2);
}
.chat-timeline__inner {
  width: min(100%, 768px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}
.chat-timeline__suggestions {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
  flex-wrap: wrap;
  margin-top: var(--space-2);
}
.chat-timeline__chip {
  padding: 8px 14px;
  border-radius: 999px;
  color: var(--color-text);
  font-size: 13px;
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}
.chat-timeline__chip:hover {
  transform: translateY(-2px);
}
.chat-timeline__jump {
  position: absolute;
  right: 20px;
  bottom: 20px;
  box-shadow: var(--shadow-md);
}
</style>
```

- [ ] **Step 2: Replace `src/components/common/EmptyState.vue`**

```vue
<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <div class="empty-state" role="status">
    <div class="empty-state__orb" aria-hidden="true" />
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: clamp(40px, 12vh, 120px) 24px 24px;
  color: var(--color-text-muted);
  text-align: center;
}
.empty-state__orb {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--accent-gradient);
  filter: blur(1px);
  box-shadow: 0 8px 30px var(--aurora-1);
}
.empty-state__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}
.empty-state__desc {
  margin: 0;
  font-size: 14px;
}
</style>
```

- [ ] **Step 3: Run tests + build**

Run: `rtk npx vitest run src/components/workspace/ChatTimeline.spec.ts`
Expected: PASS (`开始一段对话` + `订单 A2025 的物流状态？` still present; bubbles still rendered).

Run: `rtk npm run build`
Expected: passes.

- [ ] **Step 4: Commit**

```bash
rtk git add src/components/workspace/ChatTimeline.vue src/components/common/EmptyState.vue
rtk git commit -m "feat: center chat reading column with glass suggestion chips"
```

---

## Task 9: Restyle ChatMessage, ChatProcessTrace, ToolCallCard (glass surfaces)

Templates and logic are unchanged (all `data-testid`s preserved); only `<style scoped>` is edited.

**Files:**
- Modify: `src/components/workspace/ChatMessage.vue`
- Modify: `src/components/workspace/ChatProcessTrace.vue`
- Modify: `src/components/workspace/ToolCallCard.vue`

- [ ] **Step 1: In `ChatMessage.vue`, replace the `.chat-message__bubble` and `.chat-message--user .chat-message__bubble` rules**

Replace these two rules in the `<style scoped>` block with:

```css
.chat-message__bubble {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  backdrop-filter: blur(calc(var(--glass-blur) - 6px)) saturate(130%);
  -webkit-backdrop-filter: blur(calc(var(--glass-blur) - 6px)) saturate(130%);
  box-shadow: var(--shadow-sm);
  overflow-wrap: anywhere;
}

.chat-message--user .chat-message__bubble {
  border-color: var(--color-accent);
  background: var(--color-primary-soft-strong);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
```

Leave the `[data-errored='true']`, `__content`, `__cursor`, `__actions`, the `@keyframes chat-blink`, and the global `.markdown` `<style>` block as-is.

- [ ] **Step 2: In `ChatProcessTrace.vue`, replace the `.process-trace` rule**

Replace the existing `.process-trace { max-width: 100%; }` rule with:

```css
.process-trace {
  max-width: 100%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  backdrop-filter: blur(calc(var(--glass-blur) - 8px)) saturate(130%);
  -webkit-backdrop-filter: blur(calc(var(--glass-blur) - 8px)) saturate(130%);
}
```

Leave all other `.process-trace__*` rules untouched.

- [ ] **Step 3: In `ToolCallCard.vue`, replace the `.tool-call` rule**

Replace the existing `.tool-call { … }` rule with:

```css
.tool-call {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg);
  backdrop-filter: blur(calc(var(--glass-blur) - 8px)) saturate(130%);
  -webkit-backdrop-filter: blur(calc(var(--glass-blur) - 8px)) saturate(130%);
}
```

Leave `.tool-call[data-failed='true']`, `__header`, `__name`, `__label`, `__block` untouched.

- [ ] **Step 4: Run tests + build**

Run: `rtk npm run test`
Expected: all PASS (no testids/structure changed).

Run: `rtk npm run build`
Expected: passes.

- [ ] **Step 5: Commit**

```bash
rtk git add src/components/workspace/ChatMessage.vue src/components/workspace/ChatProcessTrace.vue src/components/workspace/ToolCallCard.vue
rtk git commit -m "feat: glass surfaces for chat bubbles and process trace"
```

---

## Task 10: Restyle ComposerBar (glass pill + gradient send)

**Files:**
- Modify: `src/components/workspace/ComposerBar.vue` (full replacement)

- [ ] **Step 1: Replace `src/components/workspace/ComposerBar.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue';
import { NIcon, NInput } from 'naive-ui';

import { SendIcon, StopIcon } from '@/theme/icons';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{
  (e: 'submit', message: string): void;
  (e: 'stop'): void;
}>();

const MAX_LEN = 2000;
const value = ref('');
const helperId = 'composer-helper-text';

const trimmed = computed(() => value.value.trim());
const canSend = computed(() => trimmed.value.length > 0 && !props.busy);
const overLimit = computed(() => value.value.length > MAX_LEN);

function submit() {
  if (!canSend.value || overLimit.value) return;
  emit('submit', trimmed.value);
  value.value = '';
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    submit();
  }
}
</script>

<template>
  <form class="composer glass" @submit.prevent="submit" data-testid="composer">
    <NInput
      v-model:value="value"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      placeholder="给智能助手发消息…  (Enter 发送 · Shift+Enter 换行)"
      :aria-describedby="helperId"
      :maxlength="MAX_LEN + 200"
      data-testid="composer-input"
      class="composer__input"
      @keydown="onKeydown"
    />

    <div class="composer__footer">
      <p :id="helperId" class="composer__helper" role="status" aria-live="polite">
        <span v-if="busy">正在整理回复…</span>
        <span v-else>Enter 发送 · Shift + Enter 换行</span>
        <span class="composer__count" :data-over="overLimit">{{ value.length }}/{{ MAX_LEN }}</span>
      </p>

      <button
        v-if="!busy"
        type="button"
        class="composer__send"
        :disabled="!canSend || overLimit"
        data-testid="composer-submit"
        @click="submit"
      >
        <NIcon :component="SendIcon" /><span>发送</span>
      </button>
      <button
        v-else
        type="button"
        class="composer__stop"
        data-testid="composer-stop"
        @click="emit('stop')"
      >
        <NIcon :component="StopIcon" /><span>停止</span>
      </button>
    </div>
  </form>
</template>

<style scoped>
.composer {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-3) var(--space-2);
  border-radius: var(--radius-xl);
}
.composer__input :deep(.n-input),
.composer__input :deep(.n-input .n-input__textarea-el) {
  background: transparent;
}
.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-1);
}
.composer__helper {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
}
.composer__count[data-over='true'] {
  color: var(--color-danger);
  font-weight: 600;
}
.composer__send,
.composer__stop {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: 0;
  border-radius: 999px;
  font-weight: 600;
  color: #fff;
  transition: transform 0.15s ease, opacity 0.2s ease, box-shadow 0.2s ease;
}
.composer__send {
  background: var(--accent-gradient);
  box-shadow: 0 6px 18px var(--aurora-1);
}
.composer__send:hover:not(:disabled) {
  transform: translateY(-1px);
}
.composer__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.composer__stop {
  background: var(--color-danger);
}
.composer__stop:hover {
  transform: translateY(-1px);
}
</style>
```

- [ ] **Step 2: Run the composer tests + full suite**

Run: `rtk npx vitest run src/components/workspace/ComposerBar.spec.ts`
Expected: PASS — native buttons keep `composer-submit`/`composer-stop` test-ids; disabled submit does not fire on whitespace; Enter submits; busy shows `正在整理回复…`.

Run: `rtk npm run test`
Expected: all PASS.

- [ ] **Step 3: Commit**

```bash
rtk git add src/components/workspace/ComposerBar.vue
rtk git commit -m "feat: glass composer with gradient send button"
```

---

## Task 11: layout.css cleanup + global reduced-motion

**Files:**
- Modify: `src/styles/layout.css` (full replacement)

- [ ] **Step 1: Replace `src/styles/layout.css`**

```css
/* 404 / simple page container */
.page {
  width: min(100%, 720px);
  margin: 10vh auto;
  display: grid;
  gap: var(--space-2);
  padding: clamp(24px, 4vw, 48px);
  text-align: center;
}
.page__eyebrow {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

/* Respect reduced-motion: kill aurora drift, transitions, smooth scroll */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
```

- [ ] **Step 2: Verify build + tests**

Run: `rtk npm run build`
Expected: passes (`NotFoundView` still uses `.page`/`.page__eyebrow`).

Run: `rtk npm run test`
Expected: all PASS.

- [ ] **Step 3: Commit**

```bash
rtk git add src/styles/layout.css
rtk git commit -m "refactor: trim layout.css and honor reduced-motion"
```

---

## Task 12: Update e2e specs for the single page

**Files:**
- Modify: `tests/e2e/smoke.spec.ts` (full replacement)
- Modify: `tests/e2e/theme-toggle.spec.ts` (full replacement)
- Modify: `tests/e2e/console.spec.ts` (full replacement)

- [ ] **Step 1: Replace `tests/e2e/smoke.spec.ts`**

```ts
import { expect, test } from '@playwright/test';

test('renders the chat workspace', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('session-rail')).toBeVisible();
  await expect(page.getByTestId('composer-input')).toBeVisible();
  await expect(page.getByRole('button', { name: '新对话' })).toBeVisible();
});
```

- [ ] **Step 2: Replace `tests/e2e/theme-toggle.spec.ts`**

```ts
import { expect, test } from '@playwright/test';

test.describe('theme toggle', () => {
  test('cycles auto → light → dark → auto and persists', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');
    await expect(toggle).toBeVisible();
    const html = page.locator('html');

    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'light');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('light');

    await toggle.click();
    await expect(html).toHaveAttribute('data-theme', 'dark');
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');

    await toggle.click();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');

    await page.reload();
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('auto');
  });

  test('persists dark mode across reload', async ({ page }) => {
    await page.goto('/');

    const toggle = page.getByTestId('theme-toggle');
    await toggle.click();
    await toggle.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });
});
```

- [ ] **Step 3: Replace `tests/e2e/console.spec.ts`**

```ts
import { expect, test, type Page } from '@playwright/test';

async function mockChat(page: Page) {
  await page.route('**/api/chat/stream', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream; charset=utf-8',
      body: [
        'event:thought\ndata:已接收客户诉求\n\n',
        'event:action\ndata:queryData | {"table":"orders"}\n\n',
        'event:observation\ndata:命中 1 条记录\n\n',
        'event:answer\ndata:已为你\n\n',
        'event:answer\ndata:处理完成\n\n',
        'event:done\ndata:流程结束\n\n',
      ].join(''),
    });
  });
}

test.describe('single-agent chat', () => {
  test('streams an answer and shows the process trace', async ({ page }) => {
    await mockChat(page);
    await page.goto('/');

    await expect(page.getByTestId('session-rail')).toBeVisible();
    await expect(page.getByTestId('composer-input')).toBeVisible();

    await page.getByTestId('composer-input').locator('textarea').fill('帮我查一下订单');
    await page.getByTestId('composer-submit').click();

    await expect(
      page.getByTestId('chat-message-user').last().getByTestId('chat-message-content')
    ).toHaveText('帮我查一下订单');
    await expect(
      page.getByTestId('chat-message-assistant').last().getByTestId('chat-message-content')
    ).toHaveText('已为你处理完成');

    await expect(page.getByTestId('chat-process-trace')).toBeVisible();
    await page.getByTestId('chat-process-trace').getByText('思考过程').click();
    await expect(page.getByText('已接收客户诉求')).toBeVisible();
  });

  test.describe('mobile', () => {
    test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });

    test('opens the session rail from the drawer', async ({ page }) => {
      await mockChat(page);
      await page.goto('/');

      await expect(page.getByTestId('composer-input')).toBeVisible();
      await page.getByRole('button', { name: '打开会话' }).click();
      await expect(page.getByTestId('session-rail')).toBeVisible();
      await expect(page.getByRole('button', { name: '新对话' })).toBeVisible();
    });
  });
});
```

- [ ] **Step 4: Run e2e**

Run: `rtk npm run test:e2e`
Expected: all e2e specs PASS (the `pretest:e2e` hook installs chromium; the dev server auto-starts via `playwright.config.ts`).

- [ ] **Step 5: Commit**

```bash
rtk git add tests/e2e/smoke.spec.ts tests/e2e/theme-toggle.spec.ts tests/e2e/console.spec.ts
rtk git commit -m "test: rewrite e2e specs for single-agent chat page"
```

---

## Task 13: Final verification

- [ ] **Step 1: Full build**

Run: `rtk npm run build`
Expected: `vue-tsc` clean, `vite build` succeeds.

- [ ] **Step 2: Full unit suite**

Run: `rtk npm run test`
Expected: all PASS. Confirm no spec imports a deleted module.

- [ ] **Step 3: Full e2e suite**

Run: `rtk npm run test:e2e`
Expected: all PASS.

- [ ] **Step 4: Manual smoke (optional but recommended)**

Run: `rtk npm run dev`, open `http://localhost:5173`, verify: aurora gradient + glass rail/pane render; new conversation, switch, delete work; sending streams an answer with a glass thinking-trace; theme toggle cycles light/dark/auto; layout collapses to a drawer under 980px.

- [ ] **Step 5: Commit any residual changes**

```bash
rtk git add -A
rtk git commit -m "chore: final verification for single-agent chat redesign"
```

---

## Self-Review

**Spec coverage:** §2.1 topology → Tasks 3–4; §2.2 routing → Task 4; §2.3 store (lifecycle + de-coupling) → Tasks 2, 5; §2.4 token sync → Task 1; §3 visual → Tasks 1, 3, 7–10; §4 motion/reduced-motion → Tasks 3, 11; §5 a11y (aria-labels on menu/theme/dropdown, focus-visible from reset, semantic header/main/aside/nav) → Tasks 3, 7, 10; §6 delete list → Tasks 4, 6; §7 tests → Tasks 2, 3, 5, 7, 12; §8 risks (import cleanup via vue-tsc, glass cost, token double-write, deterministic e2e) → addressed across tasks. No gaps.

**Type/name consistency:** `newConversation`, `removeSession`, `activateSession`, `bootstrap`, `sendMessage`, `stopStreaming` used identically in store, `ChatLayout`, and tests. `SessionState` = `{ id, title, messages }` everywhere after Task 5. Emits `select`/`create`/`delete` align between `SessionRail` and `ChatLayout`. Test-ids (`session-rail`, `composer-input`, `composer-submit`, `composer-stop`, `theme-toggle`, `chat-message-{role}`, `chat-message-content`, `chat-process-trace`, `chat-bubble-{id}`) preserved across restyles and asserted in specs.

**Ordering keeps the build green:** additive token/store/layout tasks (1–3) precede the cut-over (4); store ticket-removal (5) precedes feature deletion (6); restyles (7–11) are pure presentation over a working app; e2e (12) last.
