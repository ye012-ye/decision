# decision-web 前端现代化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/specs/2026-04-11-frontend-modernization-design.md` 把 `decision-web/` 前端从自研 CSS 暗色主题重构为 Naive UI + 亮色为主的现代化产品，核心升级工作台 ReAct 过程可视化，二级页跟随新主题。

**Architecture:** 引入 Naive UI + `@vicons/ionicons5`，用 CSS 变量 tokens（`tokens.css`）作为亮/暗双主题的单一真源，`theme/index.ts` 提供对应的 Naive `themeOverrides`，`stores/theme.ts` 持久化主题并跟随系统偏好。`providers/AppProviders.vue` 统一挂载 `NConfigProvider` + `NMessageProvider` 等。布局改为 TopBar + Sidebar 两部分，工作台引入新组件 `ChatMessage` / `ChatProcessTrace` / `ToolCallCard` 把 SSE `thought|action|observation|answer` 组织成步骤时间线与工具调用卡。

**Tech Stack:** Vue 3.5 + Vite 6 + TypeScript + Pinia + vue-router + Naive UI 2.x + @vicons/ionicons5 + marked + highlight.js + Vitest + @testing-library/vue + Playwright.

---

## 全局规则

所有命令在 `decision-web/` 目录下执行（如无特别说明）。

提交策略：每个 Task 末尾 commit 一次；commit message 按 Conventional Commits 格式，scope 统一 `decision-web`。

测试检查点：
- Task 0 之后、每个 Task 完成时都必须跑 `npm run test` 保持绿
- 结构性 Task（标 **[e2e]**）还要跑 `npx playwright test --config ./playwright.config.ts` — 需先启动 dev server（见 `playwright.config.ts`）或按现有 e2e 做法让 Playwright 自启动
- 每个 Task 末尾跑 `npm run build` 确保构建可通过

每次运行命令若失败，不要盲目继续 — 先诊断根因。

---

## 文件结构（重构后）

**新增**
- `src/styles/tokens.css` — CSS 变量（亮=默认 / `html[data-theme='dark']` 覆盖）
- `src/styles/layout.css` — 从 `theme.css` 抽出的通用布局类
- `src/theme/index.ts` — `lightOverrides` / `darkOverrides` GlobalThemeOverrides
- `src/theme/icons.ts` — 图标 re-export
- `src/stores/theme.ts` — 主题 Pinia store
- `src/providers/AppProviders.vue` — Config/Loading/Dialog/Notification/Message 层叠 Provider
- `src/providers/MessageApiSetup.vue` — 挂载 `$message` 等到 `window`
- `src/layouts/TopBar.vue` — 顶部栏
- `src/components/workspace/ChatMessage.vue`
- `src/components/workspace/ChatProcessTrace.vue`
- `src/components/workspace/ToolCallCard.vue`
- `src/components/common/EmptyState.vue`（简单空态复用组件，工作台用）
- `src/utils/markdown.ts` — marked + highlight.js 封装

**修改**
- `src/main.ts` — 移除旧 `theme.css` 引入；引入 `tokens.css` + `layout.css`；`bootstrap` 主题 store
- `src/App.vue` — 用 `AppProviders` 包裹 `AppShell`
- `src/layouts/AppShell.vue` — 加 TopBar + 响应式 Drawer
- `src/components/common/SidebarNav.vue` — `NMenu` 重写
- `src/components/workspace/ChatTimeline.vue` — 变薄
- `src/components/workspace/ComposerBar.vue` — NInput + 快捷键 + autosize + 发送/停止切换
- `src/components/workspace/SessionRail.vue` — 搜索 + 新建 + 激活态重绘
- `src/components/workspace/ContextPanel.vue` — `NCard` + `NForm` + `$message`
- `src/views/WorkspaceView.vue` — 使用新组件名；去掉旧 page header
- `src/views/KnowledgeView.vue` — 轻度跟随：`NCard` + `NDataTable`
- `src/views/TicketsView.vue` — 轻度跟随：`NForm` + `NDataTable`
- `src/components/knowledge/KnowledgeSidebar.vue`
- `src/components/knowledge/KnowledgeDocumentTable.vue`
- `src/components/tickets/TicketFilters.vue`
- `src/components/tickets/TicketList.vue`
- `src/components/tickets/TicketDetailPanel.vue`
- `src/stores/workspace.ts` — 新增 `stopStreaming` action + 保存 AbortController
- `src/api/chat.ts` — `streamChat` 接受 `AbortSignal`
- `src/env.d.ts` — 声明 `window.$message / $dialog / $notification / $loadingBar`
- `src/components/workspace/ChatTimeline.spec.ts` — 选择器换 testid
- `src/components/workspace/ComposerBar.spec.ts` — 同上
- `src/views/WorkspaceView.spec.ts` — 同上
- `src/layouts/AppShell.spec.ts` — 适配 AppProviders 包裹

**删除（在最后一个 Task 执行）**
- `src/styles/theme.css`

---

## Task 0: 测试选择器前置化（testid 改造）

为防止后续组件重写时测试大量挂掉，先把现有单测/e2e 中用到的 class / 结构选择器改为 `data-testid`。

**Files:**
- Modify: `src/components/workspace/ChatTimeline.vue`
- Modify: `src/components/workspace/ChatTimeline.spec.ts`
- Modify: `src/components/workspace/ComposerBar.vue`
- Modify: `src/components/workspace/ComposerBar.spec.ts`
- Modify: `src/views/WorkspaceView.spec.ts`
- Modify: `tests/console-chat.spec.ts`（若存在且有 class 选择器）

- [ ] **Step 1: 在 `ChatTimeline.vue` 的消息 article 加 `data-testid`**

修改 `src/components/workspace/ChatTimeline.vue` 的 `<article>` 元素：

```vue
<article
  v-for="message in messages"
  :key="message.id"
  class="chat-timeline__message"
  :class="`chat-timeline__message--${message.role}`"
  :data-message-id="message.id"
  :data-testid="`chat-message-${message.role}`"
>
```

在气泡上也加 testid：

```vue
<div class="chat-timeline__bubble" :data-testid="`chat-bubble-${message.id}`">
```

- [ ] **Step 2: 把 `ChatTimeline.spec.ts` 的 class 选择器换为 testid**

把下面这两处（原文件行 41-44）：

```ts
expect(container.querySelector('[data-message-id="user-1"] .chat-timeline__bubble')).toBeInTheDocument();
expect(
  container.querySelector('[data-message-id="assistant-1"] .chat-timeline__bubble')
).toBeInTheDocument();
```

改为：

```ts
expect(screen.getByTestId('chat-bubble-user-1')).toBeInTheDocument();
expect(screen.getByTestId('chat-bubble-assistant-1')).toBeInTheDocument();
```

- [ ] **Step 3: 在 `ComposerBar.vue` 的 textarea 与发送按钮加 testid**

修改 `src/components/workspace/ComposerBar.vue`：

```vue
<textarea
  v-model="value"
  class="composer__input"
  rows="3"
  placeholder="输入客户诉求或问题..."
  :aria-describedby="helperId"
  data-testid="composer-input"
/>
```

```vue
<button
  class="composer__button"
  :disabled="busy"
  type="submit"
  data-testid="composer-submit"
>
```

- [ ] **Step 4: `ComposerBar.spec.ts` 里改成 byTestId 的断言（增强而非替换文本断言）**

保留原有 `getByPlaceholderText` / `getByRole` 断言不变，仅新增一条 sanity check：

在 `'shows idle helper text, send button, and helper association for textarea'` 用例末尾追加：

```ts
expect(screen.getByTestId('composer-input')).toBeInTheDocument();
expect(screen.getByTestId('composer-submit')).toBeInTheDocument();
```

- [ ] **Step 5: 查看 `WorkspaceView.spec.ts` 与 `tests/console-chat.spec.ts`**

执行：

```bash
grep -n "querySelector\|getBy.*class\|chat-timeline__\|composer__" src/views/WorkspaceView.spec.ts tests/console-chat.spec.ts 2>/dev/null || true
```

对每一处 class 选择器，换成 testid 或 role/text 选择器。**如果该文件没有 class 选择器，跳过无改动。**

- [ ] **Step 6: 跑测试**

```bash
npm run test
```

预期：全绿。若失败，先修复再继续。

- [ ] **Step 7: Commit**

```bash
git add src/components/workspace/ChatTimeline.vue \
        src/components/workspace/ChatTimeline.spec.ts \
        src/components/workspace/ComposerBar.vue \
        src/components/workspace/ComposerBar.spec.ts \
        src/views/WorkspaceView.spec.ts \
        tests/console-chat.spec.ts
git commit -m "test(decision-web): switch selectors to data-testid for refactor safety"
```

---

## Task 1: 安装依赖

**Files:**
- Modify: `package.json`

- [ ] **Step 1: 安装运行时依赖**

```bash
npm install naive-ui @vicons/ionicons5 marked highlight.js
```

- [ ] **Step 2: 安装类型依赖（marked 的类型随包，highlight.js 自带）**

无需额外。

- [ ] **Step 3: 验证启动**

```bash
npm run dev
```

浏览器能打开，控制台无报错，Ctrl+C 停止。

- [ ] **Step 4: 跑测试 + 构建**

```bash
npm run test && npm run build
```

预期：全绿；build 产物增大但成功。

- [ ] **Step 5: Commit**

```bash
git add package.json package-lock.json
git commit -m "chore(decision-web): add naive-ui, ionicons5, marked, highlight.js"
```

---

## Task 2: 主题 Tokens（CSS 变量）

**Files:**
- Create: `src/styles/tokens.css`

- [ ] **Step 1: 创建 `src/styles/tokens.css`**

写入：

```css
/*
 * Design Tokens — Single source of truth for colors, radii, shadows, spacing.
 * ⚠️ Color values here must stay in sync with src/theme/index.ts (Naive overrides).
 */

:root {
  color-scheme: light;

  /* Surfaces */
  --color-bg: #f7f8fa;
  --color-surface: #ffffff;
  --color-surface-sunken: #f0f2f5;
  --color-surface-hover: rgba(15, 23, 42, 0.04);

  /* Borders */
  --color-border: #e5e7eb;
  --color-border-strong: #d1d5db;

  /* Text */
  --color-text: #1f2329;
  --color-text-muted: #6b7280;
  --color-text-subtle: #9ca3af;

  /* Brand & status */
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-primary-pressed: #1e40af;
  --color-primary-soft: rgba(37, 99, 235, 0.1);
  --color-primary-soft-strong: rgba(37, 99, 235, 0.16);
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-danger:  #ef4444;

  /* Radii */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 24px;

  /* Shadows */
  --shadow-sm: 0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.08);
  --shadow-md: 0 4px 12px rgba(16, 24, 40, 0.08);
  --shadow-lg: 0 12px 32px rgba(16, 24, 40, 0.12);

  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;

  /* Typography */
  --font-sans: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif;
  --font-mono: "JetBrains Mono", "SFMono-Regular", Menlo, Consolas, monospace;
}

html[data-theme='dark'] {
  color-scheme: dark;

  --color-bg: #0b0f17;
  --color-surface: #141a24;
  --color-surface-sunken: #0f141d;
  --color-surface-hover: rgba(255, 255, 255, 0.04);

  --color-border: #232a36;
  --color-border-strong: #2f3847;

  --color-text: #e6ebf2;
  --color-text-muted: #8a94a6;
  --color-text-subtle: #5f6a7d;

  --color-primary: #f0aa52;
  --color-primary-hover: #e89a3a;
  --color-primary-pressed: #d68a2a;
  --color-primary-soft: rgba(240, 170, 82, 0.12);
  --color-primary-soft-strong: rgba(240, 170, 82, 0.22);
  --color-success: #40c2ad;
  --color-warning: #f0aa52;
  --color-danger:  #f07863;

  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.28), 0 1px 3px rgba(0, 0, 0, 0.35);
  --shadow-md: 0 6px 20px rgba(0, 0, 0, 0.35);
  --shadow-lg: 0 20px 60px rgba(0, 0, 0, 0.4);
}

body {
  background: var(--color-bg);
  color: var(--color-text);
  font-family: var(--font-sans);
}
```

- [ ] **Step 2: 暂不引入 `tokens.css`**

`main.ts` 的引入放到 Task 5（和 layout.css 一起），现在只是写文件。

- [ ] **Step 3: 跑测试 + 构建**

```bash
npm run test && npm run build
```

预期：仍然绿（该文件暂未被引入）。

- [ ] **Step 4: Commit**

```bash
git add src/styles/tokens.css
git commit -m "feat(decision-web): add design token css (light/dark single source)"
```

---

## Task 3: 主题 Pinia Store（TDD）

**Files:**
- Create: `src/stores/theme.ts`
- Create: `src/stores/theme.spec.ts`

- [ ] **Step 1: 先写失败测试**

创建 `src/stores/theme.spec.ts`：

```ts
import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useThemeStore } from './theme';

function mockMatchMedia(darkPrefers: boolean) {
  const listeners: Array<(e: { matches: boolean }) => void> = [];
  vi.stubGlobal('matchMedia', vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('dark') ? darkPrefers : false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn((_evt: string, cb: (e: { matches: boolean }) => void) => listeners.push(cb)),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  return {
    trigger(matches: boolean) {
      listeners.forEach((cb) => cb({ matches }));
    },
  };
}

describe('useThemeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('defaults to auto and resolves using system preference', () => {
    mockMatchMedia(true);
    const store = useThemeStore();
    expect(store.mode).toBe('auto');
    expect(store.resolved).toBe('dark');
  });

  it('init() writes data-theme to <html>', () => {
    mockMatchMedia(false);
    const store = useThemeStore();
    store.init();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('setMode persists and updates html data-theme', () => {
    mockMatchMedia(true);
    const store = useThemeStore();
    store.setMode('light');
    expect(localStorage.getItem('theme')).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(store.resolved).toBe('light');
  });

  it('setMode("auto") follows system preference change', () => {
    const mm = mockMatchMedia(false);
    const store = useThemeStore();
    store.init();
    store.setMode('auto');
    expect(document.documentElement.dataset.theme).toBe('light');
    mm.trigger(true);
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('reads persisted mode from localStorage on creation', () => {
    localStorage.setItem('theme', 'dark');
    mockMatchMedia(false);
    const store = useThemeStore();
    expect(store.mode).toBe('dark');
    expect(store.resolved).toBe('dark');
  });
});
```

- [ ] **Step 2: 运行测试并确认失败**

```bash
npm run test -- stores/theme
```

预期：FAIL，提示找不到 `./theme` 模块。

- [ ] **Step 3: 实现 store**

创建 `src/stores/theme.ts`：

```ts
import { defineStore } from 'pinia';

export type ThemeMode = 'light' | 'dark' | 'auto';

const STORAGE_KEY = 'theme';

function readPersisted(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark' || stored === 'auto') {
    return stored;
  }
  return 'auto';
}

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: readPersisted() as ThemeMode,
  }),
  getters: {
    resolved(state): 'light' | 'dark' {
      if (state.mode === 'auto') {
        return systemPrefersDark() ? 'dark' : 'light';
      }
      return state.mode;
    },
  },
  actions: {
    setMode(mode: ThemeMode) {
      this.mode = mode;
      localStorage.setItem(STORAGE_KEY, mode);
      document.documentElement.dataset.theme = this.resolved;
    },
    init() {
      document.documentElement.dataset.theme = this.resolved;
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      media.addEventListener('change', () => {
        if (this.mode === 'auto') {
          document.documentElement.dataset.theme = this.resolved;
        }
      });
    },
  },
});
```

- [ ] **Step 4: 运行测试并确认通过**

```bash
npm run test -- stores/theme
```

预期：PASS（5 用例）。

- [ ] **Step 5: 跑完整测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 6: Commit**

```bash
git add src/stores/theme.ts src/stores/theme.spec.ts
git commit -m "feat(decision-web): add theme store with persistence and system sync"
```

---

## Task 4: Naive Theme Overrides + 图标 barrel

**Files:**
- Create: `src/theme/index.ts`
- Create: `src/theme/icons.ts`

- [ ] **Step 1: 创建 `src/theme/index.ts`**

```ts
// ⚠️ Colors here MUST match src/styles/tokens.css. Keep in sync on any edit.
import type { GlobalThemeOverrides } from 'naive-ui';

const radii = { small: '8px', medium: '12px', large: '16px' };
const fontFamily =
  '"Noto Sans SC","PingFang SC","Microsoft YaHei",system-ui,-apple-system,sans-serif';

const light = {
  primary: '#2563eb',
  primaryHover: '#1d4ed8',
  primaryPressed: '#1e40af',
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#ef4444',
  text: '#1f2329',
  textMuted: '#6b7280',
  border: '#e5e7eb',
  surface: '#ffffff',
};

const dark = {
  primary: '#f0aa52',
  primaryHover: '#e89a3a',
  primaryPressed: '#d68a2a',
  success: '#40c2ad',
  warning: '#f0aa52',
  danger: '#f07863',
  text: '#e6ebf2',
  textMuted: '#8a94a6',
  border: '#232a36',
  surface: '#141a24',
};

function makeOverrides(c: typeof light): GlobalThemeOverrides {
  return {
    common: {
      primaryColor: c.primary,
      primaryColorHover: c.primaryHover,
      primaryColorPressed: c.primaryPressed,
      primaryColorSuppl: c.primaryHover,
      successColor: c.success,
      warningColor: c.warning,
      errorColor: c.danger,
      textColorBase: c.text,
      borderRadius: radii.medium,
      borderRadiusSmall: radii.small,
      fontFamily,
    },
    Button: {
      borderRadiusTiny: radii.small,
      borderRadiusSmall: radii.small,
      borderRadiusMedium: radii.medium,
      borderRadiusLarge: radii.medium,
    },
    Card: {
      borderRadius: radii.large,
      paddingMedium: '20px 24px',
    },
    Input: {
      borderRadius: radii.medium,
    },
    Menu: {
      itemHeight: '40px',
      borderRadius: radii.medium,
    },
    Tag: {
      borderRadius: radii.small,
    },
  };
}

export const lightOverrides = makeOverrides(light);
export const darkOverrides = makeOverrides(dark);
```

- [ ] **Step 2: 创建 `src/theme/icons.ts`**

```ts
// Centralized icon imports. Add new icons here to keep tree-shaking efficient.
export {
  Search as SearchIcon,
  NotificationsOutline as BellIcon,
  PersonCircleOutline as UserIcon,
  SunnyOutline as SunIcon,
  MoonOutline as MoonIcon,
  DesktopOutline as AutoIcon,
  MenuOutline as MenuIcon,
  ChatbubblesOutline as ChatIcon,
  LibraryOutline as KnowledgeIcon,
  TicketOutline as TicketIcon,
  AddOutline as AddIcon,
  EllipsisHorizontal as MoreIcon,
  CopyOutline as CopyIcon,
  RefreshOutline as RefreshIcon,
  ThumbsUpOutline as ThumbsUpIcon,
  ThumbsDownOutline as ThumbsDownIcon,
  ArrowDownOutline as ArrowDownIcon,
  SendOutline as SendIcon,
  StopCircleOutline as StopIcon,
  BulbOutline as BulbIcon,
  ConstructOutline as ToolIcon,
  CheckmarkCircleOutline as CheckIcon,
  AlertCircleOutline as AlertIcon,
} from '@vicons/ionicons5';
```

- [ ] **Step 3: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 4: Commit**

```bash
git add src/theme/index.ts src/theme/icons.ts
git commit -m "feat(decision-web): add naive theme overrides and icon barrel"
```

---

## Task 5: Layout.css 拆分 + main.ts 切换样式入口

从现有 `src/styles/theme.css` 把与 **token 无关的布局类** 抽出到 `layout.css`；`theme.css` 暂时保留（为了工作台当前视图不崩），只是不再是主入口。

**Files:**
- Create: `src/styles/layout.css`
- Modify: `src/main.ts`

- [ ] **Step 1: 创建 `src/styles/layout.css`**

写入（把 `theme.css` 中纯布局性质的类搬过来，颜色全部走 var）：

```css
/* App shell layout */
.app-shell {
  display: grid;
  grid-template-rows: 56px 1fr;
  min-height: 100vh;
  background: var(--color-bg);
  color: var(--color-text);
}

.app-shell__body {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 0;
}

.app-shell__main {
  min-width: 0;
  padding: clamp(16px, 2.5vw, 32px);
  overflow-y: auto;
}

/* Page container */
.page {
  width: min(100%, 1600px);
  margin: 0 auto;
  display: grid;
  gap: var(--space-4);
  padding: clamp(18px, 2.2vw, 28px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.page__eyebrow {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

/* Workspace layout */
.workspace {
  width: min(100%, 1600px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr) 340px;
  gap: var(--space-4);
  min-height: calc(100vh - 56px - 40px);
}

.workspace__center {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--space-4);
  min-width: 0;
  min-height: 0;
}

.workspace__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.workspace__header h1 {
  margin: 4px 0 0;
  font-size: clamp(1.6rem, 2vw, 2rem);
  letter-spacing: -0.02em;
  color: var(--color-text);
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 272px minmax(0, 1fr);
  }
}

@media (max-width: 980px) {
  .app-shell__body {
    grid-template-columns: 1fr;
  }
  .workspace {
    grid-template-columns: 1fr;
  }
  .workspace__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
```

- [ ] **Step 2: 修改 `src/main.ts`**

替换为：

```ts
import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';
import './styles/reset.css';
import './styles/tokens.css';
import './styles/layout.css';
import './styles/theme.css';
import { useThemeStore } from './stores/theme';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

useThemeStore().init();

app.mount('#app');
```

`theme.css` 仍在尾部引入是有意的 — 它的旧类还被若干组件使用，我们到 Task 18 才删除。

- [ ] **Step 3: 视觉验证**

```bash
npm run dev
```

打开 `http://localhost:5173`，应能看到页面（亮色为默认，因为 html 上会打 `data-theme="light"`）。由于旧 theme.css 还在，实际显示会是"新底色 + 旧组件样式"混合 — **属于预期过渡态**。Ctrl+C。

- [ ] **Step 4: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 5: Commit**

```bash
git add src/styles/layout.css src/main.ts
git commit -m "feat(decision-web): split layout.css, wire tokens and theme store in main"
```

---

## Task 6: AppProviders + window.$message 类型声明

**Files:**
- Create: `src/providers/AppProviders.vue`
- Create: `src/providers/MessageApiSetup.vue`
- Modify: `src/env.d.ts`
- Modify: `src/App.vue`

- [ ] **Step 1: 补全 `src/env.d.ts` 类型**

在文件末尾追加：

```ts
import type { MessageApiInjection } from 'naive-ui/es/message/src/MessageProvider';
import type { DialogApiInjection } from 'naive-ui/es/dialog/src/DialogProvider';
import type { NotificationApiInjection } from 'naive-ui/es/notification/src/NotificationProvider';
import type { LoadingBarApiInjection } from 'naive-ui/es/loading-bar/src/LoadingBarProvider';

declare global {
  interface Window {
    $message: MessageApiInjection;
    $dialog: DialogApiInjection;
    $notification: NotificationApiInjection;
    $loadingBar: LoadingBarApiInjection;
  }
}

export {};
```

- [ ] **Step 2: 创建 `src/providers/MessageApiSetup.vue`**

```vue
<script setup lang="ts">
import { onMounted } from 'vue';
import { useDialog, useLoadingBar, useMessage, useNotification } from 'naive-ui';

const message = useMessage();
const dialog = useDialog();
const notification = useNotification();
const loadingBar = useLoadingBar();

onMounted(() => {
  window.$message = message;
  window.$dialog = dialog;
  window.$notification = notification;
  window.$loadingBar = loadingBar;
});
</script>

<template>
  <!-- pure side-effect component -->
</template>
```

- [ ] **Step 3: 创建 `src/providers/AppProviders.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import {
  NConfigProvider,
  NDialogProvider,
  NLoadingBarProvider,
  NMessageProvider,
  NNotificationProvider,
  darkTheme,
  lightTheme,
} from 'naive-ui';

import { useThemeStore } from '@/stores/theme';
import { darkOverrides, lightOverrides } from '@/theme';
import MessageApiSetup from './MessageApiSetup.vue';

const theme = useThemeStore();
const naiveTheme = computed(() => (theme.resolved === 'dark' ? darkTheme : lightTheme));
const overrides = computed(() => (theme.resolved === 'dark' ? darkOverrides : lightOverrides));
</script>

<template>
  <NConfigProvider :theme="naiveTheme" :theme-overrides="overrides">
    <NLoadingBarProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <NMessageProvider>
            <MessageApiSetup />
            <slot />
          </NMessageProvider>
        </NNotificationProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
```

- [ ] **Step 4: 修改 `src/App.vue`**

```vue
<template>
  <AppProviders>
    <AppShell />
  </AppProviders>
</template>

<script setup lang="ts">
import AppShell from '@/layouts/AppShell.vue';
import AppProviders from '@/providers/AppProviders.vue';
</script>
```

- [ ] **Step 5: 跑测试 + 构建**

```bash
npm run test && npm run build
```

预期：全绿。`AppShell.spec.ts` 若直接 mount `App.vue` 可能受影响 — 检查并在必要时把测试改为直接 mount `AppShell.vue` 不经过 Providers（单元测试不关心 Providers）。

- [ ] **Step 6: 修复 `AppShell.spec.ts`（如需要）**

打开 `src/layouts/AppShell.spec.ts`，若 import 的是 `App.vue` 改为：

```ts
import AppShell from './AppShell.vue';
```

单元测试里不挂 Providers — 原先 sidebar 导航断言继续有效；若有 Naive 组件报错，按需在 render 里传 `global: { plugins: [createPinia()] }` 或使用 `shallow` mount。

```bash
npm run test
```

必须全绿。

- [ ] **Step 7: Commit**

```bash
git add src/providers/AppProviders.vue \
        src/providers/MessageApiSetup.vue \
        src/env.d.ts \
        src/App.vue \
        src/layouts/AppShell.spec.ts
git commit -m "feat(decision-web): add AppProviders with naive config + global message apis"
```

---

## Task 7: SidebarNav 用 NMenu 重写

**Files:**
- Modify: `src/components/common/SidebarNav.vue`

- [ ] **Step 1: 重写文件**

完整替换 `src/components/common/SidebarNav.vue`：

```vue
<script setup lang="ts">
import { computed, h } from 'vue';
import { NIcon, NMenu } from 'naive-ui';
import type { MenuOption } from 'naive-ui';
import { RouterLink, useRoute } from 'vue-router';

import { ChatIcon, KnowledgeIcon, TicketIcon } from '@/theme/icons';

const route = useRoute();

function renderIcon(icon: unknown) {
  return () => h(NIcon, null, { default: () => h(icon as never) });
}

function renderLabel(path: string, text: string) {
  return () => h(RouterLink, { to: path }, { default: () => text });
}

const options = computed<MenuOption[]>(() => [
  { key: '/workspace', label: renderLabel('/workspace', '工作台'), icon: renderIcon(ChatIcon) },
  { key: '/knowledge', label: renderLabel('/knowledge', '知识库'), icon: renderIcon(KnowledgeIcon) },
  { key: '/tickets',   label: renderLabel('/tickets',   '工单'),   icon: renderIcon(TicketIcon) },
]);

const activeKey = computed(() => {
  if (route.path.startsWith('/workspace')) return '/workspace';
  if (route.path.startsWith('/knowledge')) return '/knowledge';
  if (route.path.startsWith('/tickets')) return '/tickets';
  return '/workspace';
});
</script>

<template>
  <aside class="sidebar-nav" data-testid="sidebar-nav" aria-label="主导航">
    <NMenu :options="options" :value="activeKey" :indent="16" root-indent="16" />
    <div class="sidebar-nav__footer">
      <span>v0.0.1</span>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 12px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.sidebar-nav__footer {
  margin-top: auto;
  padding: 12px 8px 4px;
  color: var(--color-text-subtle);
  font-size: 12px;
  letter-spacing: 0.08em;
}
</style>
```

- [ ] **Step 2: 若 `AppShell.spec.ts` 有依赖 "工作台" 链接文字，确认仍然通过**

```bash
npm run test -- AppShell
```

必要时调整断言：新的 DOM 里 "工作台" 仍是 link 文本，`screen.getByRole('link', { name: '工作台' })` 依然有效。

- [ ] **Step 3: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 4: Commit**

```bash
git add src/components/common/SidebarNav.vue
git commit -m "feat(decision-web): rewrite SidebarNav using NMenu + icons"
```

---

## Task 8: TopBar 组件

**Files:**
- Create: `src/layouts/TopBar.vue`

- [ ] **Step 1: 创建 `src/layouts/TopBar.vue`**

```vue
<script setup lang="ts">
import { computed, h } from 'vue';
import { NAvatar, NBadge, NButton, NDropdown, NIcon, NInput, NTooltip } from 'naive-ui';
import type { DropdownOption } from 'naive-ui';

import {
  AutoIcon,
  BellIcon,
  MenuIcon,
  MoonIcon,
  SearchIcon,
  SunIcon,
  UserIcon,
} from '@/theme/icons';
import { useThemeStore } from '@/stores/theme';

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void;
  (e: 'open-search'): void;
}>();

const theme = useThemeStore();

const themeIcon = computed(() => {
  if (theme.mode === 'light') return SunIcon;
  if (theme.mode === 'dark') return MoonIcon;
  return AutoIcon;
});

const themeLabel = computed(() => {
  if (theme.mode === 'light') return '亮色';
  if (theme.mode === 'dark') return '暗色';
  return '跟随系统';
});

function cycleTheme() {
  const next = theme.mode === 'light' ? 'dark' : theme.mode === 'dark' ? 'auto' : 'light';
  theme.setMode(next);
}

const userOptions: DropdownOption[] = [
  { key: 'profile', label: '个人资料' },
  { key: 'settings', label: '设置' },
  { type: 'divider', key: 'd1' },
  { key: 'logout', label: '退出登录' },
];

function onUserSelect(key: string) {
  window.$message?.info(`${key}（占位）`);
}

function renderIcon(icon: unknown) {
  return () => h(NIcon, null, { default: () => h(icon as never) });
}
</script>

<template>
  <header class="top-bar" data-testid="top-bar">
    <div class="top-bar__left">
      <NButton
        class="top-bar__menu"
        quaternary
        circle
        :render-icon="renderIcon(MenuIcon)"
        aria-label="打开导航"
        @click="emit('toggle-sidebar')"
      />
      <span class="top-bar__brand">决策中心</span>
    </div>

    <div class="top-bar__center">
      <NInput
        class="top-bar__search"
        placeholder="搜索会话、工单、知识…  (Ctrl/Cmd + K)"
        readonly
        @click="emit('open-search')"
      >
        <template #prefix>
          <NIcon :component="SearchIcon" />
        </template>
      </NInput>
    </div>

    <div class="top-bar__right">
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton
            quaternary
            circle
            :render-icon="renderIcon(themeIcon)"
            :aria-label="`切换主题（当前：${themeLabel}）`"
            data-testid="theme-toggle"
            @click="cycleTheme"
          />
        </template>
        当前：{{ themeLabel }}
      </NTooltip>

      <NBadge :value="0" :show="false">
        <NButton quaternary circle :render-icon="renderIcon(BellIcon)" aria-label="通知" />
      </NBadge>

      <NDropdown :options="userOptions" trigger="click" @select="onUserSelect">
        <NAvatar round :size="32" color="var(--color-primary)">
          <NIcon :component="UserIcon" />
        </NAvatar>
      </NDropdown>
    </div>
  </header>
</template>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  height: 56px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.top-bar__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.top-bar__menu {
  display: none;
}

.top-bar__brand {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text);
}

.top-bar__center {
  display: flex;
  justify-content: center;
}

.top-bar__search {
  max-width: 520px;
}

.top-bar__right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 980px) {
  .top-bar__menu {
    display: inline-flex;
  }
  .top-bar__search {
    display: none;
  }
}
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/layouts/TopBar.vue
git commit -m "feat(decision-web): add TopBar with theme toggle, search, user menu"
```

---

## Task 9: AppShell 接入 TopBar + 响应式 Drawer **[e2e]**

**Files:**
- Modify: `src/layouts/AppShell.vue`

- [ ] **Step 1: 重写 `src/layouts/AppShell.vue`**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { NDrawer, NDrawerContent } from 'naive-ui';

import SidebarNav from '@/components/common/SidebarNav.vue';
import TopBar from './TopBar.vue';

const drawerOpen = ref(false);
const isMobile = ref(false);

function evaluateViewport() {
  isMobile.value = window.matchMedia('(max-width: 980px)').matches;
}

onMounted(() => {
  evaluateViewport();
  window.addEventListener('resize', evaluateViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', evaluateViewport);
});

const sidebarVisible = computed(() => !isMobile.value);

function openSearch() {
  window.$dialog?.info({
    title: '全局搜索',
    content: '全局搜索将在后续版本提供。',
    positiveText: '好的',
  });
}
</script>

<template>
  <div class="app-shell">
    <TopBar @toggle-sidebar="drawerOpen = true" @open-search="openSearch" />
    <div class="app-shell__body">
      <SidebarNav v-if="sidebarVisible" />
      <NDrawer v-else v-model:show="drawerOpen" :width="280" placement="left">
        <NDrawerContent title="导航" closable>
          <SidebarNav />
        </NDrawerContent>
      </NDrawer>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 修 `AppShell.spec.ts`**

旧 spec 直接 mount `AppShell.vue` 并检查 Sidebar 链接。现在 TopBar / NDrawer 在无 AppProviders 情况下可能警告。测试内用 `render` + 手动 stub：

```ts
import { render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';

import AppShell from './AppShell.vue';
import WorkspaceView from '@/views/WorkspaceView.vue';

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/workspace', component: WorkspaceView, name: 'workspace' }],
});

describe('AppShell', () => {
  it('renders top bar and sidebar nav', async () => {
    router.push('/workspace');
    await router.isReady();
    render(AppShell, { global: { plugins: [createPinia(), router] } });
    expect(screen.getByTestId('top-bar')).toBeInTheDocument();
    expect(screen.getByTestId('sidebar-nav')).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: 跑单测**

```bash
npm run test
```

若 NMenu / NDrawer 报 config-provider 警告，可以在 test 里加 `global: { stubs: { NDrawer: true, NDrawerContent: true } }` 抑制 — 但**不能 stub TopBar/SidebarNav** 本身。

- [ ] **Step 4: 跑 e2e**

```bash
npm run test:e2e
```

预期：console-chat e2e 基本行为不变，能进入工作台、聊天流走通。如有断言因新增 TopBar 失败（如 `locator('body').first()` 位置偏差），按文字定位原则修正。

- [ ] **Step 5: 构建**

```bash
npm run build
```

- [ ] **Step 6: Commit**

```bash
git add src/layouts/AppShell.vue src/layouts/AppShell.spec.ts
git commit -m "feat(decision-web): wire TopBar + responsive sidebar drawer into AppShell"
```

---

## Task 10: Markdown 渲染工具

**Files:**
- Create: `src/utils/markdown.ts`
- Create: `src/utils/markdown.spec.ts`

- [ ] **Step 1: 先写失败测试**

创建 `src/utils/markdown.spec.ts`：

```ts
import { describe, expect, it } from 'vitest';

import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('renders basic markdown to html', () => {
    const html = renderMarkdown('**bold** and *italic*');
    expect(html).toContain('<strong>bold</strong>');
    expect(html).toContain('<em>italic</em>');
  });

  it('renders fenced code blocks', () => {
    const html = renderMarkdown('```js\nconst a = 1;\n```');
    expect(html).toContain('<pre');
    expect(html).toContain('const');
  });

  it('escapes inline text safely', () => {
    const html = renderMarkdown('plain <script>alert(1)</script> text');
    expect(html).not.toContain('<script>alert(1)</script>');
  });
});
```

- [ ] **Step 2: 运行并确认失败**

```bash
npm run test -- utils/markdown
```

预期：FAIL。

- [ ] **Step 3: 实现 `src/utils/markdown.ts`**

```ts
import { marked } from 'marked';
import hljs from 'highlight.js/lib/core';
import javascript from 'highlight.js/lib/languages/javascript';
import typescript from 'highlight.js/lib/languages/typescript';
import json from 'highlight.js/lib/languages/json';
import bash from 'highlight.js/lib/languages/bash';
import sql from 'highlight.js/lib/languages/sql';
import xml from 'highlight.js/lib/languages/xml';
import 'highlight.js/styles/github.css';

hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('shell', bash);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('html', xml);

marked.use({
  breaks: true,
  gfm: true,
  renderer: {
    code(code: string, lang: string | undefined) {
      const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext';
      const highlighted =
        language === 'plaintext'
          ? escapeHtml(code)
          : hljs.highlight(code, { language }).value;
      return `<pre class="hljs"><code class="language-${language}">${highlighted}</code></pre>`;
    },
  },
});

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderMarkdown(source: string): string {
  if (!source) return '';
  return marked.parse(source, { async: false }) as string;
}
```

- [ ] **Step 4: 跑测试并确认通过**

```bash
npm run test -- utils/markdown
```

预期：PASS。

- [ ] **Step 5: 跑全量 + 构建**

```bash
npm run test && npm run build
```

检查 bundle 增幅：`dist/assets/*.js` 大小对比 Task 1 之后。若 gz 增量 > 120KB 重新审视 highlight 语言注册。

- [ ] **Step 6: Commit**

```bash
git add src/utils/markdown.ts src/utils/markdown.spec.ts
git commit -m "feat(decision-web): add markdown renderer with selective hljs languages"
```

---

## Task 11: ToolCallCard 组件

**Files:**
- Create: `src/components/workspace/ToolCallCard.vue`

- [ ] **Step 1: 创建文件**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { NCode, NTag } from 'naive-ui';

const props = defineProps<{
  toolName: string;
  args: string;
  result?: string;
  durationMs?: number;
  failed?: boolean;
}>();

const argsPretty = computed(() => tryFormatJson(props.args));
const resultPretty = computed(() => (props.result ? tryFormatJson(props.result) : ''));

function tryFormatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
</script>

<template>
  <div class="tool-call" :data-failed="failed">
    <header class="tool-call__header">
      <span class="tool-call__name">{{ toolName }}</span>
      <NTag v-if="durationMs !== undefined" size="small" :bordered="false">{{ durationMs }}ms</NTag>
      <NTag v-if="failed" size="small" type="error" :bordered="false">失败</NTag>
    </header>

    <section class="tool-call__block">
      <p class="tool-call__label">参数</p>
      <NCode :code="argsPretty" language="json" word-wrap />
    </section>

    <section v-if="result" class="tool-call__block">
      <p class="tool-call__label">结果</p>
      <NCode :code="resultPretty" language="json" word-wrap />
    </section>
  </div>
</template>

<style scoped>
.tool-call {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
}

.tool-call[data-failed='true'] {
  border-color: var(--color-danger);
}

.tool-call__header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.tool-call__name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.tool-call__label {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.tool-call__block {
  display: grid;
  gap: 4px;
}
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/components/workspace/ToolCallCard.vue
git commit -m "feat(decision-web): add ToolCallCard for SSE action/observation visualization"
```

---

## Task 12: ChatProcessTrace 组件

把 `ChatAssistantMessage.process[]` 中的 `thought` / `action` / `observation` 组织成步骤时间线。`action` 和其紧邻的下一条 `observation` 合并成一张 `ToolCallCard`。

**Files:**
- Create: `src/components/workspace/ChatProcessTrace.vue`

- [ ] **Step 1: 创建文件**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { NCollapse, NCollapseItem, NIcon, NTag } from 'naive-ui';

import { BulbIcon, ToolIcon } from '@/theme/icons';
import type { ChatAssistantMessage, ChatProcessEntry } from '@/types/chat';
import ToolCallCard from './ToolCallCard.vue';

const props = defineProps<{
  message: ChatAssistantMessage;
}>();

type Step =
  | { kind: 'thought'; id: string; text: string }
  | { kind: 'tool'; id: string; toolName: string; args: string; result?: string };

const steps = computed<Step[]>(() => {
  const out: Step[] = [];
  const entries = props.message.process;
  for (let i = 0; i < entries.length; i += 1) {
    const entry = entries[i];
    if (entry.type === 'thought') {
      out.push({ kind: 'thought', id: entry.id, text: entry.content });
      continue;
    }
    if (entry.type === 'action') {
      const { toolName, args } = parseAction(entry.content);
      const next = entries[i + 1];
      const result = next && next.type === 'observation' ? next.content : undefined;
      if (result !== undefined) {
        i += 1;
      }
      out.push({ kind: 'tool', id: entry.id, toolName, args, result });
      continue;
    }
    if (entry.type === 'observation') {
      // observation without preceding action — render as plain tool with empty args
      out.push({ kind: 'tool', id: entry.id, toolName: '(observation)', args: '', result: entry.content });
    }
  }
  return out;
});

function parseAction(content: string): { toolName: string; args: string } {
  // backend encodes action as "toolName | arguments"
  const pipeIdx = content.indexOf('|');
  if (pipeIdx === -1) {
    return { toolName: content.trim(), args: '' };
  }
  return {
    toolName: content.slice(0, pipeIdx).trim(),
    args: content.slice(pipeIdx + 1).trim(),
  };
}

const stepCount = computed(() => steps.value.length);

const expandedNames = computed<string[]>(() =>
  props.message.status === 'streaming' || props.message.status === 'error' ? ['trace'] : []
);
</script>

<template>
  <NCollapse
    v-if="stepCount > 0"
    class="process-trace"
    :expanded-names="expandedNames"
    arrow-placement="left"
    data-testid="chat-process-trace"
  >
    <NCollapseItem name="trace">
      <template #header>
        <div class="process-trace__header">
          <NIcon :component="BulbIcon" />
          <span>思考过程</span>
          <NTag size="tiny" :bordered="false">{{ stepCount }} 步</NTag>
          <NTag
            v-if="message.status === 'streaming'"
            size="tiny"
            type="info"
            :bordered="false"
          >进行中</NTag>
          <NTag
            v-else-if="message.status === 'error'"
            size="tiny"
            type="error"
            :bordered="false"
          >失败</NTag>
        </div>
      </template>

      <ol class="process-trace__list">
        <li v-for="(step, index) in steps" :key="step.id" class="process-trace__step">
          <span class="process-trace__number">{{ index + 1 }}</span>
          <div class="process-trace__body">
            <template v-if="step.kind === 'thought'">
              <p class="process-trace__kind">
                <NIcon :component="BulbIcon" /> 思考
              </p>
              <p class="process-trace__text">{{ step.text }}</p>
            </template>
            <template v-else>
              <p class="process-trace__kind">
                <NIcon :component="ToolIcon" /> 调用工具
              </p>
              <ToolCallCard
                :tool-name="step.toolName"
                :args="step.args"
                :result="step.result"
              />
            </template>
          </div>
        </li>
      </ol>
    </NCollapseItem>
  </NCollapse>
</template>

<style scoped>
.process-trace {
  max-width: 100%;
}

.process-trace__header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
}

.process-trace__list {
  margin: 0;
  padding: 0 0 0 var(--space-2);
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.process-trace__step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: var(--space-3);
  align-items: start;
}

.process-trace__number {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.process-trace__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.process-trace__kind {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.process-trace__text {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/components/workspace/ChatProcessTrace.vue
git commit -m "feat(decision-web): add ChatProcessTrace with thought/tool step layout"
```

---

## Task 13: ChatMessage 组件

**Files:**
- Create: `src/components/workspace/ChatMessage.vue`

- [ ] **Step 1: 创建文件**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { NAvatar, NButton, NIcon, NTooltip } from 'naive-ui';

import { CopyIcon, RefreshIcon, ThumbsDownIcon, ThumbsUpIcon, UserIcon } from '@/theme/icons';
import type { ChatMessage } from '@/types/chat';
import { renderMarkdown } from '@/utils/markdown';
import ChatProcessTrace from './ChatProcessTrace.vue';

const props = defineProps<{ message: ChatMessage }>();

const isAssistant = computed(() => props.message.role === 'assistant');

const html = computed(() => {
  if (props.message.role !== 'assistant') return '';
  return renderMarkdown(props.message.content);
});

const streaming = computed(
  () => props.message.role === 'assistant' && props.message.status === 'streaming'
);

const errored = computed(
  () => props.message.role === 'assistant' && props.message.status === 'error'
);

async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.message.content);
    window.$message?.success('已复制');
  } catch {
    window.$message?.error('复制失败');
  }
}
</script>

<template>
  <article
    class="chat-message"
    :class="`chat-message--${message.role}`"
    :data-testid="`chat-message-${message.role}`"
  >
    <NAvatar
      v-if="isAssistant"
      class="chat-message__avatar"
      round
      :size="32"
      color="var(--color-primary)"
    >
      AI
    </NAvatar>

    <div class="chat-message__body">
      <ChatProcessTrace v-if="isAssistant" :message="(message as any)" />

      <div
        class="chat-message__bubble"
        :data-testid="`chat-bubble-${message.id}`"
        :data-streaming="streaming"
        :data-errored="errored"
      >
        <template v-if="isAssistant">
          <div class="chat-message__content markdown" v-html="html" />
          <span v-if="streaming" class="chat-message__cursor" aria-hidden="true">▊</span>
        </template>
        <template v-else>
          <p class="chat-message__content chat-message__content--user">{{ message.content }}</p>
        </template>
      </div>

      <div v-if="isAssistant && !streaming" class="chat-message__actions">
        <NTooltip>
          <template #trigger>
            <NButton quaternary size="tiny" circle @click="copyContent">
              <template #icon><NIcon :component="CopyIcon" /></template>
            </NButton>
          </template>
          复制
        </NTooltip>
        <NTooltip>
          <template #trigger>
            <NButton quaternary size="tiny" circle disabled>
              <template #icon><NIcon :component="RefreshIcon" /></template>
            </NButton>
          </template>
          重新生成（即将上线）
        </NTooltip>
        <NButton quaternary size="tiny" circle disabled>
          <template #icon><NIcon :component="ThumbsUpIcon" /></template>
        </NButton>
        <NButton quaternary size="tiny" circle disabled>
          <template #icon><NIcon :component="ThumbsDownIcon" /></template>
        </NButton>
      </div>
    </div>

    <NAvatar
      v-if="!isAssistant"
      class="chat-message__avatar chat-message__avatar--user"
      round
      :size="32"
    >
      <NIcon :component="UserIcon" />
    </NAvatar>
  </article>
</template>

<style scoped>
.chat-message {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.chat-message--user {
  flex-direction: row-reverse;
}

.chat-message__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
  max-width: min(80%, 52rem);
}

.chat-message--user .chat-message__body {
  max-width: min(68%, 44rem);
  justify-items: flex-end;
}

.chat-message__bubble {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-sunken);
  overflow-wrap: anywhere;
}

.chat-message--user .chat-message__bubble {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.chat-message__bubble[data-errored='true'] {
  border-color: var(--color-danger);
}

.chat-message__content {
  margin: 0;
  line-height: 1.7;
  font-size: 15px;
  color: var(--color-text);
}

.chat-message__content--user {
  white-space: pre-wrap;
}

.chat-message__cursor {
  display: inline-block;
  width: 8px;
  animation: chat-blink 1s steps(1) infinite;
  color: var(--color-primary);
}

@keyframes chat-blink {
  50% { opacity: 0; }
}

.chat-message__actions {
  display: flex;
  gap: 4px;
  opacity: 0.78;
}
</style>

<style>
.markdown p { margin: 0 0 8px; }
.markdown p:last-child { margin-bottom: 0; }
.markdown pre {
  margin: 8px 0;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--color-bg);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 13px;
}
.markdown code { font-family: var(--font-mono); font-size: 13px; }
.markdown :not(pre) > code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-surface-hover);
}
.markdown ul, .markdown ol { padding-left: 20px; margin: 4px 0; }
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/components/workspace/ChatMessage.vue
git commit -m "feat(decision-web): add ChatMessage with markdown, streaming cursor, actions"
```

---

## Task 14: ChatTimeline 变薄 + 滚动到底按钮 **[e2e]**

**Files:**
- Modify: `src/components/workspace/ChatTimeline.vue`
- Modify: `src/components/workspace/ChatTimeline.spec.ts`
- Create: `src/components/common/EmptyState.vue`

- [ ] **Step 1: 创建 `src/components/common/EmptyState.vue`**

```vue
<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <div class="empty-state" role="status">
    <svg class="empty-state__art" viewBox="0 0 120 80" aria-hidden="true">
      <rect x="10" y="14" width="100" height="52" rx="10" fill="none" stroke="currentColor" stroke-width="2" />
      <circle cx="34" cy="40" r="4" fill="currentColor" />
      <circle cx="60" cy="40" r="4" fill="currentColor" />
      <circle cx="86" cy="40" r="4" fill="currentColor" />
    </svg>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-text-muted);
}
.empty-state__art {
  width: 120px;
  color: var(--color-text-subtle);
}
.empty-state__title {
  margin: 0;
  font-size: 15px;
  color: var(--color-text);
}
.empty-state__desc {
  margin: 0;
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: 重写 `ChatTimeline.vue`**

```vue
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { NButton, NIcon, NTag } from 'naive-ui';

import { ArrowDownIcon } from '@/theme/icons';
import type { ChatMessage } from '@/types/chat';
import ChatMessage from './ChatMessage.vue';
import EmptyState from '@/components/common/EmptyState.vue';

const props = defineProps<{ messages: ChatMessage[] }>();
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
      <EmptyState
        v-if="messages.length === 0"
        title="开始一段对话"
        description="发送一条问题，或试试下面的示例："
      >
        <div class="chat-timeline__suggestions">
          <NTag
            v-for="suggestion in suggestions"
            :key="suggestion"
            checkable
            round
            @update:checked="emit('suggest', suggestion)"
          >
            {{ suggestion }}
          </NTag>
        </div>
      </EmptyState>

      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :message="message"
      />
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
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-height: 0;
  padding: var(--space-4);
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.chat-timeline__suggestions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 8px;
}

.chat-timeline__jump {
  position: absolute;
  right: 20px;
  bottom: 20px;
  box-shadow: var(--shadow-md);
}
</style>
```

- [ ] **Step 3: 更新 `ChatTimeline.spec.ts`**

```ts
import { render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';

import type { ChatAssistantMessage, ChatMessage, ChatUserMessage } from '@/types/chat';
import ChatTimeline from './ChatTimeline.vue';

const userMsg: ChatUserMessage = { id: 'user-1', role: 'user', content: '客户投诉物流慢' };
const assistantMsg: ChatAssistantMessage = {
  id: 'assistant-1',
  role: 'assistant',
  content: '我先帮你查一下',
  status: 'done',
  process: [{ id: 'p1', type: 'thought', content: '检索工单' }],
  processExpanded: false,
};

describe('ChatTimeline', () => {
  it('renders empty state with suggestions when no messages', () => {
    render(ChatTimeline, {
      props: { messages: [] },
      global: { plugins: [createPinia()] },
    });
    expect(screen.getByText('开始一段对话')).toBeInTheDocument();
    expect(screen.getByText('订单 A2025 的物流状态？')).toBeInTheDocument();
  });

  it('renders user and assistant messages via ChatMessage', () => {
    const messages: ChatMessage[] = [userMsg, assistantMsg];
    render(ChatTimeline, {
      props: { messages },
      global: { plugins: [createPinia()] },
    });
    expect(screen.getByText('客户投诉物流慢')).toBeInTheDocument();
    expect(screen.getByText('我先帮你查一下')).toBeInTheDocument();
    expect(screen.getByTestId('chat-bubble-user-1')).toBeInTheDocument();
    expect(screen.getByTestId('chat-bubble-assistant-1')).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: 跑单测**

```bash
npm run test
```

预期：全绿。旧的 "展开过程/收起过程" 用例会随着测试重写消失（现在由 NCollapse 管理，不再需要 `emit('toggle-process')`）。

- [ ] **Step 5: 跑 e2e**

```bash
npm run test:e2e
```

- [ ] **Step 6: 构建**

```bash
npm run build
```

- [ ] **Step 7: Commit**

```bash
git add src/components/common/EmptyState.vue \
        src/components/workspace/ChatTimeline.vue \
        src/components/workspace/ChatTimeline.spec.ts
git commit -m "feat(decision-web): slim ChatTimeline, add empty state and jump-to-bottom"
```

---

## Task 15: ComposerBar 升级（NInput + 快捷键 + 停止）

**Files:**
- Modify: `src/components/workspace/ComposerBar.vue`
- Modify: `src/components/workspace/ComposerBar.spec.ts`

- [ ] **Step 1: 重写 `ComposerBar.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue';
import { NButton, NIcon, NInput } from 'naive-ui';

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
  <form class="composer" @submit.prevent="submit" data-testid="composer">
    <NInput
      v-model:value="value"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      placeholder="输入客户诉求或问题... (Enter 发送 · Shift+Enter 换行)"
      :aria-describedby="helperId"
      :maxlength="MAX_LEN + 200"
      data-testid="composer-input"
      @keydown="onKeydown"
    />

    <div class="composer__footer">
      <p :id="helperId" class="composer__helper" role="status" aria-live="polite">
        <span v-if="busy">正在整理回复…</span>
        <span v-else>Enter 发送 · Shift + Enter 换行</span>
        <span class="composer__count" :data-over="overLimit">{{ value.length }}/{{ MAX_LEN }}</span>
      </p>

      <NButton
        v-if="!busy"
        type="primary"
        :disabled="!canSend || overLimit"
        data-testid="composer-submit"
        @click="submit"
      >
        <template #icon><NIcon :component="SendIcon" /></template>
        发送
      </NButton>
      <NButton
        v-else
        type="error"
        data-testid="composer-stop"
        @click="emit('stop')"
      >
        <template #icon><NIcon :component="StopIcon" /></template>
        停止
      </NButton>
    </div>
  </form>
</template>

<style scoped>
.composer {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
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
</style>
```

- [ ] **Step 2: 重写 `ComposerBar.spec.ts`**

```ts
import { fireEvent, render, screen } from '@testing-library/vue';
import { describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';

import ComposerBar from './ComposerBar.vue';

function mount(props: { busy: boolean }) {
  return render(ComposerBar, { props, global: { plugins: [createPinia()] } });
}

describe('ComposerBar', () => {
  it('renders send button and input when idle', () => {
    mount({ busy: false });
    expect(screen.getByTestId('composer-input')).toBeInTheDocument();
    expect(screen.getByTestId('composer-submit')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-stop')).not.toBeInTheDocument();
  });

  it('renders stop button and helper text when busy', () => {
    mount({ busy: true });
    expect(screen.getByText('正在整理回复…')).toBeInTheDocument();
    expect(screen.getByTestId('composer-stop')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-submit')).not.toBeInTheDocument();
  });

  it('emits trimmed message on click submit and clears field', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '  客户想改签  ');
    await fireEvent.click(screen.getByTestId('composer-submit'));
    expect(view.emitted('submit')).toEqual([['客户想改签']]);
    expect((screen.getByTestId('composer-input').querySelector('textarea')! as HTMLTextAreaElement).value).toBe('');
  });

  it('emits submit on Enter without shift', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '请帮我处理退款');
    await fireEvent.keyDown(input, { key: 'Enter' });
    expect(view.emitted('submit')).toEqual([['请帮我处理退款']]);
  });

  it('does not emit submit on Shift+Enter', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, 'line1');
    await fireEvent.keyDown(input, { key: 'Enter', shiftKey: true });
    expect(view.emitted('submit')).toBeUndefined();
  });

  it('does not emit submit for whitespace only', async () => {
    const view = mount({ busy: false });
    const input = screen.getByTestId('composer-input').querySelector('textarea')!;
    await fireEvent.update(input, '   ');
    await fireEvent.click(screen.getByTestId('composer-submit'));
    expect(view.emitted('submit')).toBeUndefined();
  });

  it('emits stop when stop button clicked while busy', async () => {
    const view = mount({ busy: true });
    await fireEvent.click(screen.getByTestId('composer-stop'));
    expect(view.emitted('stop')).toEqual([[]]);
  });
});
```

- [ ] **Step 3: 跑单测**

```bash
npm run test -- ComposerBar
```

预期：7 用例全 PASS。

- [ ] **Step 4: 跑完整 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 5: Commit**

```bash
git add src/components/workspace/ComposerBar.vue \
        src/components/workspace/ComposerBar.spec.ts
git commit -m "feat(decision-web): upgrade ComposerBar with autosize, shortcuts, stop button"
```

---

## Task 16: SessionRail 重写

**Files:**
- Modify: `src/components/workspace/SessionRail.vue`

- [ ] **Step 1: 重写文件**

```vue
<script setup lang="ts">
import { computed, ref, h } from 'vue';
import { NButton, NDropdown, NEmpty, NIcon, NInput, NScrollbar } from 'naive-ui';

import { AddIcon, MoreIcon, SearchIcon } from '@/theme/icons';
import type { ChatMessage } from '@/types/chat';

const props = defineProps<{
  sessions: Array<{ id: string; title: string; messages?: ChatMessage[] }>;
  activeSessionId: string;
}>();
const emit = defineEmits<{
  (e: 'select', id: string): void;
  (e: 'create'): void;
}>();

const query = ref('');

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.sessions;
  return props.sessions.filter((s) => s.title.toLowerCase().includes(q));
});

const dropdownOptions = [
  { key: 'rename', label: '重命名' },
  { key: 'delete', label: '删除', props: { style: 'color: var(--color-danger)' } },
];

function onDropdown(key: string) {
  window.$message?.info(`${key}（占位）`);
}

function renderIcon(icon: unknown) {
  return () => h(NIcon, null, { default: () => h(icon as never) });
}
</script>

<template>
  <aside class="session-rail" data-testid="session-rail">
    <header class="session-rail__header">
      <NInput v-model:value="query" placeholder="搜索会话" size="small" clearable>
        <template #prefix><NIcon :component="SearchIcon" /></template>
      </NInput>
      <NButton type="primary" size="small" :render-icon="renderIcon(AddIcon)" @click="emit('create')">
        新建
      </NButton>
    </header>

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
          <button
            type="button"
            class="session-rail__btn"
            @click="emit('select', session.id)"
          >
            <span class="session-rail__title">{{ session.title }}</span>
            <span class="session-rail__meta">{{ session.messages?.length ?? 0 }} 条记录</span>
          </button>
          <NDropdown :options="dropdownOptions" trigger="click" @select="onDropdown">
            <NButton quaternary circle size="tiny">
              <template #icon><NIcon :component="MoreIcon" /></template>
            </NButton>
          </NDropdown>
        </li>
      </ul>
    </NScrollbar>
  </aside>
</template>

<style scoped>
.session-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.session-rail__header {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  margin-bottom: var(--space-2);
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
  padding: 0 4px 0 10px;
  border-radius: var(--radius-md);
  border-left: 3px solid transparent;
}
.session-rail__item[data-active='true'] {
  border-left-color: var(--color-primary);
  background: var(--color-primary-soft);
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
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/components/workspace/SessionRail.vue
git commit -m "feat(decision-web): rewrite SessionRail with search, dropdown, active state"
```

---

## Task 17: ContextPanel 重写（NCard + NForm + $message）

**Files:**
- Modify: `src/components/workspace/ContextPanel.vue`

注意：原组件的 `emit('create', ...)` 签名（`type: 'LOGISTICS'`, `priority: 'HIGH'` 字面量）被 `WorkspaceView` 转给 `store.createTicketFromContext`，后者接受更宽的类型（见 `src/stores/workspace.ts:175`）。本 Task 保留相同 emit 签名，**不改 store**。

- [ ] **Step 1: 重写文件**

```vue
<script setup lang="ts">
import { computed, reactive } from 'vue';
import { NButton, NCard, NForm, NFormItem, NInput, NTag } from 'naive-ui';

const props = defineProps<{
  context: { ticketOrderNo: string; activeTab: string };
}>();

const emit = defineEmits<{
  (e: 'create', payload: {
    type: 'LOGISTICS';
    title: string;
    description: string;
    customerId: string;
    priority: 'HIGH';
  }): void;
}>();

const draft = reactive({
  customerId: '',
  title: '',
  description: '',
});

const loading = computed(() => false); // reserved for future busy state
const canSubmit = computed(() =>
  Boolean(draft.customerId.trim() && draft.title.trim() && draft.description.trim())
);

async function submit() {
  if (!canSubmit.value) return;
  try {
    emit('create', {
      type: 'LOGISTICS',
      title: draft.title.trim(),
      description: draft.description.trim(),
      customerId: draft.customerId.trim(),
      priority: 'HIGH',
    });
    window.$message?.success('已发起工单创建');
  } catch (error) {
    window.$message?.error(error instanceof Error ? error.message : '创建失败');
  }
}
</script>

<template>
  <NCard
    class="context-panel"
    data-testid="context-panel"
    :bordered="true"
    content-style="padding: 20px 22px;"
  >
    <template #header>
      <div class="context-panel__header">
        <span>上下文</span>
        <NTag size="small" :type="context.activeTab === 'ticket' ? 'success' : 'default'" :bordered="false">
          {{ context.activeTab }}
        </NTag>
      </div>
    </template>

    <p v-if="context.ticketOrderNo" class="context-panel__order">
      当前工单：<strong>{{ context.ticketOrderNo }}</strong>
    </p>
    <p v-else class="context-panel__hint">命中工单号后，这里会自动切换到 ticket 视图。</p>

    <NForm label-placement="top" :show-require-mark="false">
      <NFormItem label="客户 ID">
        <NInput v-model:value="draft.customerId" placeholder="CUS-10086" />
      </NFormItem>
      <NFormItem label="工单标题">
        <NInput v-model:value="draft.title" placeholder="物流异常跟进" />
      </NFormItem>
      <NFormItem label="工单描述">
        <NInput
          v-model:value="draft.description"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 8 }"
          placeholder="补充上下文、诉求和处理建议"
        />
      </NFormItem>

      <NButton
        type="primary"
        block
        :disabled="!canSubmit"
        :loading="loading"
        data-testid="context-create"
        @click="submit"
      >
        手动创建工单
      </NButton>
    </NForm>
  </NCard>
</template>

<style scoped>
.context-panel {
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}
.context-panel__header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.context-panel__order {
  margin: 0 0 var(--space-3);
  color: var(--color-text);
}
.context-panel__hint {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 3: Commit**

```bash
git add src/components/workspace/ContextPanel.vue
git commit -m "feat(decision-web): rewrite ContextPanel with NCard/NForm and message feedback"
```

---

## Task 18: WorkspaceView 接入新组件 **[e2e]**

**Files:**
- Modify: `src/views/WorkspaceView.vue`
- Modify: `src/views/WorkspaceView.spec.ts`

- [ ] **Step 1: 重写 `WorkspaceView.vue`**

```vue
<script setup lang="ts">
import { onMounted } from 'vue';
import { NTag } from 'naive-ui';

import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import ContextPanel from '@/components/workspace/ContextPanel.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();

onMounted(() => {
  store.bootstrap();
});

function onCreateSession() {
  window.$message?.info('新建会话（占位）');
}
</script>

<template>
  <section class="workspace">
    <SessionRail
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="store.activateSession"
      @create="onCreateSession"
    />

    <div class="workspace__center">
      <header class="workspace__header">
        <div>
          <p class="page__eyebrow">智能客服</p>
          <h1>工作台</h1>
        </div>
        <NTag
          :type="store.sending ? 'warning' : 'success'"
          :bordered="false"
          round
        >
          {{ store.sending ? '正在生成回复' : '等待新指令' }}
        </NTag>
      </header>

      <ChatTimeline
        :messages="store.activeSession.messages"
        @suggest="store.sendMessage"
      />

      <ComposerBar
        :busy="store.sending"
        @submit="store.sendMessage"
        @stop="store.stopStreaming"
      />
    </div>

    <ContextPanel
      :context="store.activeSession.context"
      @create="store.createTicketFromContext"
    />
  </section>
</template>
```

注意 `store.stopStreaming` 和 `@suggest="store.sendMessage"` 在此引入。`stopStreaming` 将在 Task 19 实现；在 Task 19 完成前，**此处 TypeScript 会报 "属性不存在"**。为避免 Task 18 编译失败，先在 store 中加空占位（Step 2）。

- [ ] **Step 2: 在 `stores/workspace.ts` 加空 `stopStreaming` 占位**

在 actions 区加：

```ts
stopStreaming() {
  // placeholder; full implementation lands in Task 19
},
```

- [ ] **Step 3: 修 `WorkspaceView.spec.ts`**

用 testid 重写关键断言。打开文件，把 class-based 断言替换为 `getByTestId` / `getByText`。最少保留以下断言：

- 标题 "工作台" 可见
- `SessionRail` / `ChatTimeline` / `ComposerBar` / `ContextPanel` 都 mount
- 发送消息后 store.sendMessage 被调用

如需 mock store，使用 `createTestingPinia`：

```ts
import { createTestingPinia } from '@pinia/testing';
```

若未装该包，改用 `createPinia()` + 手动插桩 `vi.spyOn(store, 'sendMessage')`。

- [ ] **Step 4: 跑单测**

```bash
npm run test
```

预期：全绿。

- [ ] **Step 5: 跑 e2e**

```bash
npm run test:e2e
```

预期：`console-chat.spec.ts` SSE 聊天流式行为仍通过。

- [ ] **Step 6: 构建**

```bash
npm run build
```

- [ ] **Step 7: Commit**

```bash
git add src/views/WorkspaceView.vue \
        src/views/WorkspaceView.spec.ts \
        src/stores/workspace.ts
git commit -m "feat(decision-web): compose new workspace view with modern components"
```

---

## Task 19: 真正接入流式 abort（stopStreaming）

**Files:**
- Modify: `src/api/chat.ts`
- Modify: `src/stores/workspace.ts`

- [ ] **Step 1: 让 `streamChat` 接受 `AbortSignal`**

把 `src/api/chat.ts` 改为：

```ts
import type { ChatRequest, ChatStreamEvent } from '@/types/chat';
import { parseSseChunk } from '@/utils/sse';

export async function streamChat(
  request: ChatRequest,
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal,
) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`聊天请求失败: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    const text = decoder.decode(value, { stream: true });
    const parsed = parseSseChunk(text, buffer);
    buffer = parsed.remainder;
    parsed.events.forEach(onEvent);
  }
}
```

- [ ] **Step 2: 在 store 里保存 AbortController 并实现 `stopStreaming`**

修改 `src/stores/workspace.ts`：

1. 状态里加 `abortController: null as AbortController | null`
2. `sendMessage` 开头：

```ts
this.abortController?.abort();
const controller = new AbortController();
this.abortController = controller;
```

3. 把 `streamChat(...)` 调用替换为：

```ts
await streamChat({ sessionId: session.id, message }, (event) => { /* 现有回调 */ }, controller.signal);
```

4. `catch` 分支里，识别 `AbortError`：

```ts
} catch (error) {
  const aborted = error instanceof DOMException && error.name === 'AbortError';
  withAssistantMessage((target) => {
    if (aborted) {
      target.status = 'done';
      if (!target.content.trim()) target.content = '（已停止）';
      return;
    }
    target.status = 'error';
    /* 既有错误处理 */
  });
  if (!aborted) throw error;
}
```

5. `finally` 把 `this.abortController = null`。

6. 把原来的空 `stopStreaming()` 占位改为：

```ts
stopStreaming() {
  this.abortController?.abort();
},
```

- [ ] **Step 3: 单测**

如果已有 `workspace` store 的单测 mock 了 `streamChat`，确认签名变化后依然通过（新增的 `signal` 参数是可选的，旧 mock 兼容）。

```bash
npm run test
```

- [ ] **Step 4: e2e**

```bash
npm run test:e2e
```

- [ ] **Step 5: 构建**

```bash
npm run build
```

- [ ] **Step 6: Commit**

```bash
git add src/api/chat.ts src/stores/workspace.ts
git commit -m "feat(decision-web): support aborting SSE stream via stopStreaming action"
```

---

## Task 20: 知识库页轻度跟随新主题

**Files:**
- Modify: `src/views/KnowledgeView.vue`
- Modify: `src/components/knowledge/KnowledgeSidebar.vue`
- Modify: `src/components/knowledge/KnowledgeDocumentTable.vue`

不改数据流 / 不新增功能，只把关键组件换成 Naive 对应件，样式从 token 走。

- [ ] **Step 1: 先看现有 `KnowledgeSidebar.vue`**

```bash
cat src/components/knowledge/KnowledgeSidebar.vue
```

- [ ] **Step 2: 用 `NList` 改写分类侧栏**

打开 `src/components/knowledge/KnowledgeSidebar.vue`，把自研 ul/li 结构换成 Naive `NList` + `NListItem`，item 点击 emit 原有 `@select`。保留 props 签名（`bases`、`activeKbCode`、`@select`）。

模板示意：

```vue
<NList bordered>
  <NListItem
    v-for="base in bases"
    :key="base.kbCode"
    :data-active="base.kbCode === activeKbCode"
    @click="emit('select', base.kbCode)"
  >
    <template #prefix><NIcon :component="KnowledgeIcon" /></template>
    {{ base.name }}
  </NListItem>
</NList>
```

scoped 样式里用 `--color-primary-soft` 着色 active 态。

- [ ] **Step 3: 用 `NDataTable` 改写文档表格**

打开 `src/components/knowledge/KnowledgeDocumentTable.vue`。保留现有 `props.documents` 数据与 `@upload` / `@refresh` emit，列配置直接映射到 `NDataTable` `columns`：

```ts
import type { DataTableColumns } from 'naive-ui';
import type { KnowledgeDocument } from '@/types/knowledge'; // adjust path to existing type

const columns: DataTableColumns<KnowledgeDocument> = [
  { title: '文件名', key: 'fileName', ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', render: (row) => h(NTag, { type: statusType(row.status), bordered: false }, { default: () => row.status }) },
  { title: '更新时间', key: 'updatedAt' },
  // ...and action column with refresh button
];
```

按现有列结构照搬。外层卡片用 `NCard` 或沿用 `.page` 布局类。

- [ ] **Step 4: `KnowledgeView.vue` 外层**

保持布局不动，只把外层 class `knowledge-page` 相关 scoped 样式里的硬编码颜色（如 `rgba(64,194,173,...)`）换成 `var(--color-success)` 等 token。状态徽章可直接换成 `<NTag type="success">`。

- [ ] **Step 5: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 6: 手动视觉检查**

```bash
npm run dev
```

访问 `/knowledge`，确认亮/暗主题都能看。

- [ ] **Step 7: Commit**

```bash
git add src/views/KnowledgeView.vue \
        src/components/knowledge/KnowledgeSidebar.vue \
        src/components/knowledge/KnowledgeDocumentTable.vue
git commit -m "refactor(decision-web): apply naive components + tokens to knowledge view"
```

---

## Task 21: 工单页轻度跟随新主题

**Files:**
- Modify: `src/views/TicketsView.vue`
- Modify: `src/components/tickets/TicketFilters.vue`
- Modify: `src/components/tickets/TicketList.vue`
- Modify: `src/components/tickets/TicketDetailPanel.vue`

与 Task 20 同样原则：不改行为，只换组件 + 配色走 token。

- [ ] **Step 1: `TicketFilters.vue` 改用 `NForm inline` + `NSelect` / `NDatePicker` / `NInput`**

保留原 `:filters` / `:loading` props 和 `@refresh` emit。把原 label/input 结构换成 Naive 同义组件。

- [ ] **Step 2: `TicketList.vue` 换 `NDataTable`**

`columns` 映射现有字段；状态列：

```ts
{
  title: '状态',
  key: 'status',
  render(row) {
    const type = row.status === 'CLOSED' ? 'success' : row.status === 'IN_PROGRESS' ? 'info' : 'default';
    return h(NTag, { type, bordered: false }, { default: () => row.status });
  },
},
```

保持 `@select` emit 触发。

- [ ] **Step 3: `TicketDetailPanel.vue` 换 `NDescriptions` + `NTimeline`**

字段用 `<NDescriptions :column="1">` + `<NDescriptionsItem>` 展示，动作按钮（`@update-status` / `@close`）换成 `NButton`。保持 emit 签名。

- [ ] **Step 4: `TicketsView.vue` 外层状态徽章换 `NTag`**

与 KnowledgeView 同步，`<p class="tickets-page__status">` 换成 `<NTag :type="loading ? 'warning' : 'success'">`。scoped 样式里的硬编码 rgba 颜色改成 `var(...)` token。

- [ ] **Step 5: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 6: 视觉 smoke test**

```bash
npm run dev
```

访问 `/tickets`，检查亮/暗两种主题，确认表格、详情、筛选都能正常交互。

- [ ] **Step 7: Commit**

```bash
git add src/views/TicketsView.vue \
        src/components/tickets/TicketFilters.vue \
        src/components/tickets/TicketList.vue \
        src/components/tickets/TicketDetailPanel.vue
git commit -m "refactor(decision-web): apply naive components + tokens to tickets view"
```

---

## Task 22: 替换 store / API 层中残留的 alert / 裸抛

扫描项目中 `alert(`、`confirm(`、未被处理的 `throw` 提示，改为 `window.$message` / `window.$dialog`。

**Files:**
- Grep/Modify: 扫描结果决定

- [ ] **Step 1: 扫描**

```bash
grep -rn "alert(\|window.alert\|confirm(" src/
```

- [ ] **Step 2: 逐一替换**

对每个命中，把 `alert('X')` → `window.$message.info('X')`（错误场景用 `error`，成功用 `success`）。

- [ ] **Step 3: 检查 `stores/tickets.ts` / `stores/knowledge.ts` 的成功/失败提示**

```bash
grep -n "throw\|console.error" src/stores/tickets.ts src/stores/knowledge.ts
```

对裸 `throw` 的外层调用处，考虑在 `.catch` 补一次 `window.$message.error(...)` — 但不要吞掉错误（如果原本会被视图组件捕获就不要重复）。

- [ ] **Step 4: 跑测试 + 构建**

```bash
npm run test && npm run build
```

- [ ] **Step 5: Commit**

```bash
git add -u src/
git commit -m "refactor(decision-web): route user feedback through global message api"
```

---

## Task 23: 删除旧 theme.css 及遗留 class 引用 **[e2e]**

**Files:**
- Delete: `src/styles/theme.css`
- Modify: `src/main.ts`
- Modify: any components still using legacy classes

- [ ] **Step 1: 找到 theme.css 中所有类的引用**

```bash
grep -oE "[a-z-]+__[a-z-]+|\\.session-rail|\\.chat-timeline|\\.composer__|\\.context-panel" src/styles/theme.css | sort -u
```

把上面输出与 `src/**/*.vue` 做交叉检查：

```bash
grep -rn "chat-timeline\|session-rail\|composer__\|context-panel\|workspace-panel__\|sidebar__" src/
```

- [ ] **Step 2: 清理残留**

对每个仍然引用旧类名的文件：
- 若该类是已重写组件里的 scoped 样式，改成新类名或删除
- 若是 `.page__eyebrow` 等仍然通用的类，保留并确认已在 `layout.css` 中定义

- [ ] **Step 3: 从 `main.ts` 移除 theme.css 引入**

```ts
// 删除这一行：
import './styles/theme.css';
```

- [ ] **Step 4: 删除文件**

```bash
git rm src/styles/theme.css
```

- [ ] **Step 5: 跑测试 + 构建**

```bash
npm run test && npm run build
```

如有 CSS 选择器失效导致的视觉回归，回到 Step 2 补 scoped 样式。

- [ ] **Step 6: 跑 e2e**

```bash
npm run test:e2e
```

- [ ] **Step 7: 视觉 smoke test**

```bash
npm run dev
```

手动点开工作台 / 知识库 / 工单三个页面，切换亮/暗主题，确认：
- 无空白页
- 工作台发送消息 → ReAct 过程时间线正常展开/折叠
- Sidebar、TopBar 完整

- [ ] **Step 8: Commit**

```bash
git add -u src/ src/main.ts
git commit -m "chore(decision-web): remove legacy theme.css and dangling class references"
```

---

## Task 24: 主题切换 e2e 测试 **[e2e]**

**Files:**
- Create: `tests/theme-toggle.spec.ts`

- [ ] **Step 1: 新增 playwright 用例**

```ts
import { expect, test } from '@playwright/test';

test.describe('theme toggle', () => {
  test('cycles light → dark → auto and persists', async ({ page }) => {
    await page.goto('/workspace');
    const html = page.locator('html');

    const toggle = page.getByTestId('theme-toggle');
    await expect(toggle).toBeVisible();

    // auto (default) resolves via matchMedia; just force through the cycle
    await toggle.click(); // first click lands on 'dark' from auto/light
    const afterFirst = await html.getAttribute('data-theme');
    expect(afterFirst === 'dark' || afterFirst === 'light').toBeTruthy();

    await toggle.click();
    await toggle.click();

    // after three clicks we've traversed the cycle; reload and confirm persistence of mode
    const modeBefore = await page.evaluate(() => localStorage.getItem('theme'));
    expect(['light', 'dark', 'auto']).toContain(modeBefore);

    await page.reload();
    const modeAfter = await page.evaluate(() => localStorage.getItem('theme'));
    expect(modeAfter).toBe(modeBefore);
  });
});
```

- [ ] **Step 2: 运行 e2e**

```bash
npm run test:e2e
```

预期：新用例通过，旧用例仍绿。

- [ ] **Step 3: Commit**

```bash
git add tests/theme-toggle.spec.ts
git commit -m "test(decision-web): add e2e for theme toggle cycle and persistence"
```

---

## Task 25: 最终自检 + Changelog

**Files:**
- Modify: `README.md` or similar（若存在 changelog 区域）

- [ ] **Step 1: Grep 残留的 TODO / 硬编码颜色**

```bash
grep -rn "TODO\|FIXME" src/
grep -rn "#07111b\|#f0aa52\|#40c2ad" src/
```

`#f0aa52` 允许在 `tokens.css` / `theme/index.ts` 的 dark 分支内出现；其他地方出现应改成 token。

- [ ] **Step 2: `tokens.css` 与 `theme/index.ts` 颜色对照**

肉眼核对：`theme/index.ts` 的 `light` / `dark` 常量与 `tokens.css` 对应值一致。不一致则修复。

- [ ] **Step 3: 测试与构建最后跑一次**

```bash
npm run test && npm run build && npm run test:e2e
```

- [ ] **Step 4: README 追加变更说明（若 README 存在）**

```bash
test -f README.md && echo "update it" || echo "skip"
```

若存在，在顶部追加 "## 2026-04-11 前端现代化" 段落：引入 Naive UI、亮色为主、暗色保留琥珀、工作台 ReAct 过程可视化等 5 行。

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "chore(decision-web): finalize modernization pass with docs and cleanup"
```

---

## 验收

- [ ] `npm run build` 通过，bundle gzip 总大小较现状增量 ≤ 300KB
- [ ] `npm run test` 全绿
- [ ] `npm run test:e2e` 全绿（包含新增的 theme-toggle）
- [ ] 亮色 / 暗色 / auto 三种模式均可切换，刷新后保持
- [ ] 1920 / 1280 / 980 / 720 四个断点下工作台可用
- [ ] 工作台 SSE 流式行为未变：thought 实时、action/observation 在 ToolCallCard、answer 流式、done 后 ProcessTrace 自动折叠
- [ ] 生成工单成功/失败通过 `$message` 反馈
