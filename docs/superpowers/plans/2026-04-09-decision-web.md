# Decision Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone `decision-web` frontend plus frontend-facing work-order REST APIs so the project has a real smart-customer-service console that can chat, manage knowledge bases, and manage tickets.

**Architecture:** Add a new Vue 3 + Vite + TypeScript SPA at `decision-web/` and keep `decision-app` as the only browser-facing backend. Reuse the existing chat SSE and knowledge APIs, and extend `decision-app` with a dedicated `WorkOrderController` and frontend DTOs instead of reusing the AI tool DTO. Frontend state is split by feature (`workspace`, `knowledge`, `tickets`) and consumes a shared HTTP client plus an SSE parser.

**Tech Stack:** Vue 3, Vite, TypeScript, Pinia, Vue Router, Vitest, Playwright, Java 17, Spring Boot 3, MockMvc, Mockito

---

## File Structure

### New files (create)

| File | Responsibility |
|------|---------------|
| `decision-web/package.json` | Frontend scripts and dependencies |
| `decision-web/tsconfig.json` | TypeScript compiler options |
| `decision-web/tsconfig.node.json` | Vite node-side TypeScript config |
| `decision-web/vite.config.ts` | Vue plugin, path alias, dev proxy, test config |
| `decision-web/index.html` | SPA HTML entry |
| `decision-web/src/main.ts` | Bootstraps Vue, router, and Pinia |
| `decision-web/src/App.vue` | Root app shell mount |
| `decision-web/src/router/index.ts` | Route table for workspace / knowledge / tickets |
| `decision-web/src/env.d.ts` | Vite module typing |
| `decision-web/src/test/setup.ts` | Vitest setup |
| `decision-web/src/styles/reset.css` | Reset and base element defaults |
| `decision-web/src/styles/theme.css` | Design tokens, layout system, responsive rules |
| `decision-web/src/layouts/AppShell.vue` | Global navigation and route outlet |
| `decision-web/src/components/common/SidebarNav.vue` | Left navigation |
| `decision-web/src/api/http.ts` | Shared fetch wrapper for `Result<T>` |
| `decision-web/src/api/chat.ts` | Chat SSE API wrapper |
| `decision-web/src/api/knowledge.ts` | Knowledge API wrapper |
| `decision-web/src/api/tickets.ts` | Ticket API wrapper |
| `decision-web/src/types/api.ts` | Shared API envelope types |
| `decision-web/src/types/chat.ts` | Chat event types |
| `decision-web/src/types/knowledge.ts` | Knowledge DTO types |
| `decision-web/src/types/tickets.ts` | Ticket DTO types |
| `decision-web/src/utils/sse.ts` | Parses `text/event-stream` chunks |
| `decision-web/src/utils/extractors.ts` | Extracts work-order numbers and message hints |
| `decision-web/src/stores/workspace.ts` | Session list, message stream, SSE state |
| `decision-web/src/stores/knowledge.ts` | Knowledge-base state and polling |
| `decision-web/src/stores/tickets.ts` | Ticket list/detail state |
| `decision-web/src/views/WorkspaceView.vue` | Main smart-service workspace |
| `decision-web/src/views/KnowledgeView.vue` | Knowledge management page |
| `decision-web/src/views/TicketsView.vue` | Ticket management page |
| `decision-web/src/views/NotFoundView.vue` | Fallback page |
| `decision-web/src/components/workspace/SessionRail.vue` | Session list and quick actions |
| `decision-web/src/components/workspace/ChatTimeline.vue` | Message and ReAct event rendering |
| `decision-web/src/components/workspace/ContextPanel.vue` | Right-side customer/ticket/knowledge context |
| `decision-web/src/components/workspace/ComposerBar.vue` | Chat input box and action row |
| `decision-web/src/components/tickets/TicketFilters.vue` | Ticket filter form |
| `decision-web/src/components/tickets/TicketList.vue` | Ticket list |
| `decision-web/src/components/tickets/TicketDetailPanel.vue` | Ticket details, log timeline, actions |
| `decision-web/src/components/knowledge/KnowledgeSidebar.vue` | Knowledge-base list |
| `decision-web/src/components/knowledge/KnowledgeDocumentTable.vue` | Document list and upload state |
| `decision-web/src/layouts/AppShell.spec.ts` | App shell route smoke test |
| `decision-web/src/utils/sse.spec.ts` | SSE parser tests |
| `decision-web/src/stores/workspace.spec.ts` | Workspace store tests |
| `decision-web/src/stores/tickets.spec.ts` | Ticket store tests |
| `decision-web/src/stores/knowledge.spec.ts` | Knowledge store tests |
| `decision-web/playwright.config.ts` | E2E config |
| `decision-web/tests/e2e/console.spec.ts` | End-to-end smoke flow |
| `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCreateReq.java` | Frontend create-ticket request DTO |
| `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderStatusUpdateReq.java` | Frontend update-status request DTO |
| `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCloseReq.java` | Frontend close-ticket request DTO |
| `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderVO.java` | Ticket response DTO |
| `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderLogVO.java` | Ticket log response DTO |
| `decision-app/src/main/java/com/ye/decision/controller/WorkOrderController.java` | Frontend-facing work-order REST controller |
| `decision-app/src/test/java/com/ye/decision/controller/WorkOrderControllerTest.java` | MockMvc tests for work-order REST API |

### Existing files (modify)

| File | Change |
|------|--------|
| `.gitignore` | Ignore frontend build artifacts and `node_modules` |
| `decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java` | Add list/filter support and query helper methods |
| `decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java` | Add service tests for filters |

---

## Task 1: Scaffold `decision-web` and the global app shell

**Files:**
- Create: `decision-web/package.json`
- Create: `decision-web/tsconfig.json`
- Create: `decision-web/tsconfig.node.json`
- Create: `decision-web/vite.config.ts`
- Create: `decision-web/index.html`
- Create: `decision-web/src/main.ts`
- Create: `decision-web/src/App.vue`
- Create: `decision-web/src/router/index.ts`
- Create: `decision-web/src/env.d.ts`
- Create: `decision-web/src/test/setup.ts`
- Create: `decision-web/src/styles/reset.css`
- Create: `decision-web/src/styles/theme.css`
- Create: `decision-web/src/layouts/AppShell.vue`
- Create: `decision-web/src/components/common/SidebarNav.vue`
- Create: `decision-web/src/views/WorkspaceView.vue`
- Create: `decision-web/src/views/KnowledgeView.vue`
- Create: `decision-web/src/views/TicketsView.vue`
- Create: `decision-web/src/views/NotFoundView.vue`
- Create: `decision-web/src/layouts/AppShell.spec.ts`
- Modify: `.gitignore`

- [ ] **Step 1: Write the failing app-shell smoke test**

Create `decision-web/src/layouts/AppShell.spec.ts`:

```ts
import { render, screen } from '@testing-library/vue';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import { describe, expect, it } from 'vitest';

import AppShell from './AppShell.vue';
import { routes } from '../router';

describe('AppShell', () => {
  it('renders the three primary navigation links', async () => {
    setActivePinia(createPinia());
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });

    router.push('/workspace');
    await router.isReady();

    render(AppShell, {
      global: {
        plugins: [router],
      },
    });

    expect(screen.getByRole('link', { name: '工作台' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '知识库' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '工单' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the smoke test to verify the scaffold is missing**

Run:

```bash
cd decision-web
npm run test -- src/layouts/AppShell.spec.ts
```

Expected:

```text
Error: Cannot find module './AppShell.vue'
```

- [ ] **Step 3: Add the frontend scaffold and minimal shell implementation**

Create `decision-web/package.json`:

```json
{
  "name": "decision-web",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:e2e": "playwright test"
  },
  "dependencies": {
    "pinia": "^2.1.7",
    "vue": "^3.5.13",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "@playwright/test": "^1.54.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/vue": "^8.1.0",
    "@vitejs/plugin-vue": "^5.2.1",
    "@vue/tsconfig": "^0.7.0",
    "happy-dom": "^16.7.2",
    "typescript": "^5.7.3",
    "vite": "^6.0.7",
    "vitest": "^2.1.8",
    "vue-tsc": "^2.2.0"
  }
}
```

Create `decision-web/vite.config.ts`:

```ts
import { fileURLToPath, URL } from 'node:url';
import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
});
```

Create `decision-web/src/router/index.ts`:

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import KnowledgeView from '@/views/KnowledgeView.vue';
import NotFoundView from '@/views/NotFoundView.vue';
import TicketsView from '@/views/TicketsView.vue';
import WorkspaceView from '@/views/WorkspaceView.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/workspace' },
  { path: '/workspace', component: WorkspaceView, meta: { title: '工作台' } },
  { path: '/knowledge', component: KnowledgeView, meta: { title: '知识库' } },
  { path: '/tickets', component: TicketsView, meta: { title: '工单' } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '未找到页面' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
```

Create `decision-web/src/layouts/AppShell.vue`:

```vue
<script setup lang="ts">
import SidebarNav from '@/components/common/SidebarNav.vue';
</script>

<template>
  <div class="app-shell">
    <SidebarNav />
    <main class="app-shell__main">
      <RouterView />
    </main>
  </div>
</template>
```

Create `decision-web/src/components/common/SidebarNav.vue`:

```vue
<script setup lang="ts">
const links = [
  { to: '/workspace', label: '工作台' },
  { to: '/knowledge', label: '知识库' },
  { to: '/tickets', label: '工单' },
];
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand">
      <span class="sidebar__eyebrow">Decision</span>
      <strong>Console</strong>
    </div>

    <nav class="sidebar__nav" aria-label="主导航">
      <RouterLink
        v-for="link in links"
        :key="link.to"
        class="sidebar__link"
        :to="link.to"
      >
        {{ link.label }}
      </RouterLink>
    </nav>
  </aside>
</template>
```

Create `decision-web/tsconfig.json`:

```json
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"]
}
```

Create `decision-web/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts", "playwright.config.ts"]
}
```

Create `decision-web/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Decision Console</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

Create `decision-web/src/env.d.ts`:

```ts
/// <reference types="vite/client" />
```

Create `decision-web/src/test/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

Create `decision-web/src/styles/reset.css`:

```css
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body,
#app {
  margin: 0;
}

body,
button,
textarea,
input {
  font: inherit;
}

button {
  cursor: pointer;
}
```

Create `decision-web/src/views/WorkspaceView.vue`:

```vue
<template>
  <section class="page page--workspace">
    <header class="page__header">
      <p class="page__eyebrow">智能客服</p>
      <h1>工作台</h1>
    </header>
  </section>
</template>
```

Create `decision-web/src/views/KnowledgeView.vue`:

```vue
<template>
  <section class="page">
    <header class="page__header">
      <p class="page__eyebrow">RAG</p>
      <h1>知识库</h1>
    </header>
  </section>
</template>
```

Create `decision-web/src/views/TicketsView.vue`:

```vue
<template>
  <section class="page">
    <header class="page__header">
      <p class="page__eyebrow">Service</p>
      <h1>工单</h1>
    </header>
  </section>
</template>
```

Create `decision-web/src/views/NotFoundView.vue`:

```vue
<template>
  <section class="page">
    <header class="page__header">
      <p class="page__eyebrow">404</p>
      <h1>页面不存在</h1>
    </header>
  </section>
</template>
```

Create `decision-web/src/App.vue`:

```vue
<template>
  <AppShell />
</template>

<script setup lang="ts">
import AppShell from '@/layouts/AppShell.vue';
</script>
```

Create `decision-web/src/main.ts`:

```ts
import { createPinia } from 'pinia';
import { createApp } from 'vue';

import App from './App.vue';
import router from './router';
import './styles/reset.css';
import './styles/theme.css';

createApp(App).use(createPinia()).use(router).mount('#app');
```

Create `decision-web/src/styles/theme.css`:

```css
:root {
  --bg: #07111b;
  --bg-elevated: rgba(17, 29, 43, 0.72);
  --panel: rgba(11, 20, 31, 0.85);
  --line: rgba(255, 255, 255, 0.08);
  --text: #eef3f7;
  --muted: #8fa3b7;
  --accent: #f0aa52;
  --ok: #40c2ad;
  --danger: #f07863;
  --shadow: 0 20px 60px rgba(0, 0, 0, 0.28);
  font-family: "Noto Sans SC", sans-serif;
  color: var(--text);
  background:
    radial-gradient(circle at top left, rgba(240, 170, 82, 0.14), transparent 28%),
    radial-gradient(circle at right center, rgba(64, 194, 173, 0.1), transparent 24%),
    linear-gradient(180deg, #08111c 0%, #050b13 100%);
}

body {
  min-height: 100vh;
  background: transparent;
}

#app {
  min-height: 100vh;
}

.app-shell {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
}

.app-shell__main {
  padding: 32px;
}

.sidebar {
  padding: 24px;
  border-right: 1px solid var(--line);
  background: rgba(7, 17, 27, 0.78);
  backdrop-filter: blur(18px);
}

.sidebar__brand {
  display: grid;
  gap: 4px;
  margin-bottom: 24px;
}

.sidebar__eyebrow,
.page__eyebrow {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.sidebar__nav {
  display: grid;
  gap: 8px;
}

.sidebar__link {
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 14px;
  color: var(--text);
  text-decoration: none;
}

.sidebar__link.router-link-active {
  border-color: rgba(240, 170, 82, 0.35);
  background: rgba(240, 170, 82, 0.08);
}

.page {
  min-height: calc(100vh - 64px);
  border: 1px solid var(--line);
  border-radius: 28px;
  padding: 28px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

@media (max-width: 980px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .app-shell__main {
    padding: 16px;
  }
}
```

Append to `.gitignore`:

```gitignore
decision-web/node_modules/
decision-web/dist/
decision-web/.vite/
decision-web/playwright-report/
decision-web/test-results/
```

- [ ] **Step 4: Install dependencies and run the smoke test again**

Run:

```bash
cd decision-web
npm install
npm run test -- src/layouts/AppShell.spec.ts
```

Expected:

```text
✓ src/layouts/AppShell.spec.ts
```

- [ ] **Step 5: Commit the scaffold**

```bash
git add .gitignore decision-web
git commit -m "feat(frontend): scaffold decision web app shell"
```

---

## Task 2: Add frontend-facing work-order DTOs and service filter support

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCreateReq.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderStatusUpdateReq.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCloseReq.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderVO.java`
- Create: `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderLogVO.java`
- Modify: `decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java`
- Modify: `decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java`

- [ ] **Step 1: Write the failing service test for filtered listing**

Append to `decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java`:

```java
@Test
void list_appliesOptionalFiltersAndSortsByCreatedAtDesc() {
    when(workOrderMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new WorkOrderEntity()));

    List<WorkOrderEntity> result = service.list(
        "WO20260409001",
        "13800001111",
        WorkOrderStatus.PROCESSING,
        WorkOrderType.LOGISTICS,
        WorkOrderPriority.HIGH
    );

    assertThat(result).hasSize(1);
    verify(workOrderMapper).selectList(any(QueryWrapper.class));
}
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```bash
mvnw.cmd -pl decision-app -Dtest=WorkOrderServiceTest#list_appliesOptionalFiltersAndSortsByCreatedAtDesc test
```

Expected:

```text
cannot find symbol
  method list(...)
```

- [ ] **Step 3: Add the DTOs and service method**

Create `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCreateReq.java`:

```java
package com.ye.decision.domain.dto;

import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderCreateReq(
    @NotNull WorkOrderType type,
    WorkOrderPriority priority,
    @NotBlank @Size(max = 256) String title,
    @NotBlank String description,
    @NotBlank @Size(max = 64) String customerId,
    @Size(max = 128) String sessionId
) {}
```

Create `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderStatusUpdateReq.java`:

```java
package com.ye.decision.domain.dto;

import com.ye.decision.domain.enums.WorkOrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkOrderStatusUpdateReq(
    @NotNull WorkOrderStatus status,
    @Size(max = 1024) String note,
    @NotBlank @Size(max = 64) String operator
) {}
```

Create `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCloseReq.java`:

```java
package com.ye.decision.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOrderCloseReq(
    @NotBlank String resolution,
    @NotBlank @Size(max = 64) String operator
) {}
```

Create `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderVO.java`:

```java
package com.ye.decision.domain.dto;

import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;

import java.time.LocalDateTime;

public record WorkOrderVO(
    String orderNo,
    WorkOrderType type,
    WorkOrderPriority priority,
    WorkOrderStatus status,
    String title,
    String description,
    String customerId,
    String assignee,
    String assigneeGroup,
    String resolution,
    String sessionId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime resolvedAt
) {
    public static WorkOrderVO from(WorkOrderEntity entity) {
        return new WorkOrderVO(
            entity.getOrderNo(),
            entity.getType(),
            entity.getPriority(),
            entity.getStatus(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCustomerId(),
            entity.getAssignee(),
            entity.getAssigneeGroup(),
            entity.getResolution(),
            entity.getSessionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getResolvedAt()
        );
    }
}
```

Create `decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderLogVO.java`:

```java
package com.ye.decision.domain.dto;

import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.WorkOrderAction;

import java.time.LocalDateTime;

public record WorkOrderLogVO(
    WorkOrderAction action,
    String operator,
    String content,
    LocalDateTime createdAt
) {
    public static WorkOrderLogVO from(WorkOrderLogEntity entity) {
        return new WorkOrderLogVO(
            entity.getAction(),
            entity.getOperator(),
            entity.getContent(),
            entity.getCreatedAt()
        );
    }
}
```

Modify `decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java`:

```java
public List<WorkOrderEntity> list(String orderNo,
                                  String customerId,
                                  WorkOrderStatus status,
                                  WorkOrderType type,
                                  WorkOrderPriority priority) {
    QueryWrapper<WorkOrderEntity> query = new QueryWrapper<>();
    if (orderNo != null && !orderNo.isBlank()) {
        query.eq("order_no", orderNo);
    }
    if (customerId != null && !customerId.isBlank()) {
        query.eq("customer_id", customerId);
    }
    if (status != null) {
        query.eq("status", status.getCode());
    }
    if (type != null) {
        query.eq("type", type.getCode());
    }
    if (priority != null) {
        query.eq("priority", priority.getCode());
    }
    query.orderByDesc("created_at");
    return workOrderMapper.selectList(query);
}
```

- [ ] **Step 4: Run the service test to verify it passes**

Run:

```bash
mvnw.cmd -pl decision-app -Dtest=WorkOrderServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit the DTO and service changes**

```bash
git add decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCreateReq.java decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderStatusUpdateReq.java decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderCloseReq.java decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderVO.java decision-app/src/main/java/com/ye/decision/domain/dto/WorkOrderLogVO.java decision-app/src/main/java/com/ye/decision/service/WorkOrderService.java decision-app/src/test/java/com/ye/decision/service/WorkOrderServiceTest.java
git commit -m "feat(workorder): add frontend dto models and list filters"
```

---

## Task 3: Add the frontend-facing work-order REST controller

**Files:**
- Create: `decision-app/src/main/java/com/ye/decision/controller/WorkOrderController.java`
- Create: `decision-app/src/test/java/com/ye/decision/controller/WorkOrderControllerTest.java`

- [ ] **Step 1: Write the failing MockMvc test**

Create `decision-app/src/test/java/com/ye/decision/controller/WorkOrderControllerTest.java`:

```java
package com.ye.decision.controller;

import com.ye.decision.domain.entity.WorkOrderEntity;
import com.ye.decision.domain.entity.WorkOrderLogEntity;
import com.ye.decision.domain.enums.WorkOrderAction;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;
import com.ye.decision.service.WorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderController.class)
class WorkOrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    WorkOrderService workOrderService;

    @Test
    void create_returnsCreatedTicket() throws Exception {
        when(workOrderService.create(WorkOrderType.LOGISTICS, WorkOrderPriority.HIGH, "物流延迟", "三天未更新", "13800001111", "session-1"))
            .thenReturn(buildEntity());

        mockMvc.perform(post("/api/work-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type":"LOGISTICS",
                      "priority":"HIGH",
                      "title":"物流延迟",
                      "description":"三天未更新",
                      "customerId":"13800001111",
                      "sessionId":"session-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"));
    }

    @Test
    void logs_returnsOperationHistory() throws Exception {
        when(workOrderService.getLogsByOrderNo("WO20260409001"))
            .thenReturn(List.of(new WorkOrderLogEntity("WO20260409001", WorkOrderAction.CREATE, "agent", "创建工单")));

        mockMvc.perform(get("/api/work-orders/WO20260409001/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].action").value("CREATE"));
    }

    @Test
    void updateStatus_returnsUpdatedTicket() throws Exception {
        doNothing().when(workOrderService).updateStatus("WO20260409001", WorkOrderStatus.PROCESSING, "开始处理", "agent");
        when(workOrderService.queryByOrderNo("WO20260409001")).thenReturn(buildEntity());

        mockMvc.perform(patch("/api/work-orders/WO20260409001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"PROCESSING",
                      "note":"开始处理",
                      "operator":"agent"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value("WO20260409001"));
    }

    private static WorkOrderEntity buildEntity() {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setOrderNo("WO20260409001");
        entity.setType(WorkOrderType.LOGISTICS);
        entity.setPriority(WorkOrderPriority.HIGH);
        entity.setStatus(WorkOrderStatus.PENDING);
        entity.setTitle("物流延迟");
        entity.setDescription("三天未更新");
        entity.setCustomerId("13800001111");
        entity.setAssignee("物流专员");
        entity.setAssigneeGroup("物流组");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
```

- [ ] **Step 2: Run the MockMvc test and verify it fails**

Run:

```bash
mvnw.cmd -pl decision-app -Dtest=WorkOrderControllerTest test
```

Expected:

```text
ClassNotFoundException: com.ye.decision.controller.WorkOrderController
```

- [ ] **Step 3: Implement the controller**

Create `decision-app/src/main/java/com/ye/decision/controller/WorkOrderController.java`:

```java
package com.ye.decision.controller;

import com.ye.decision.common.Result;
import com.ye.decision.domain.dto.WorkOrderCloseReq;
import com.ye.decision.domain.dto.WorkOrderCreateReq;
import com.ye.decision.domain.dto.WorkOrderLogVO;
import com.ye.decision.domain.dto.WorkOrderStatusUpdateReq;
import com.ye.decision.domain.dto.WorkOrderVO;
import com.ye.decision.domain.enums.WorkOrderPriority;
import com.ye.decision.domain.enums.WorkOrderStatus;
import com.ye.decision.domain.enums.WorkOrderType;
import com.ye.decision.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public Result<List<WorkOrderVO>> list(@RequestParam(required = false) String orderNo,
                                          @RequestParam(required = false) String customerId,
                                          @RequestParam(required = false) WorkOrderStatus status,
                                          @RequestParam(required = false) WorkOrderType type,
                                          @RequestParam(required = false) WorkOrderPriority priority) {
        return Result.ok(workOrderService.list(orderNo, customerId, status, type, priority)
            .stream()
            .map(WorkOrderVO::from)
            .toList());
    }

    @PostMapping
    public Result<WorkOrderVO> create(@Valid @RequestBody WorkOrderCreateReq req) {
        return Result.ok(WorkOrderVO.from(workOrderService.create(
            req.type(),
            req.priority(),
            req.title(),
            req.description(),
            req.customerId(),
            req.sessionId()
        )));
    }

    @GetMapping("/{orderNo}")
    public Result<WorkOrderVO> getByOrderNo(@PathVariable String orderNo) {
        return Result.ok(WorkOrderVO.from(workOrderService.queryByOrderNo(orderNo)));
    }

    @PatchMapping("/{orderNo}/status")
    public Result<WorkOrderVO> updateStatus(@PathVariable String orderNo,
                                            @Valid @RequestBody WorkOrderStatusUpdateReq req) {
        workOrderService.updateStatus(orderNo, req.status(), req.note(), req.operator());
        return Result.ok(WorkOrderVO.from(workOrderService.queryByOrderNo(orderNo)));
    }

    @PostMapping("/{orderNo}/close")
    public Result<WorkOrderVO> close(@PathVariable String orderNo,
                                     @Valid @RequestBody WorkOrderCloseReq req) {
        workOrderService.close(orderNo, req.resolution(), req.operator());
        return Result.ok(WorkOrderVO.from(workOrderService.queryByOrderNo(orderNo)));
    }

    @GetMapping("/{orderNo}/logs")
    public Result<List<WorkOrderLogVO>> logs(@PathVariable String orderNo) {
        return Result.ok(workOrderService.getLogsByOrderNo(orderNo)
            .stream()
            .map(WorkOrderLogVO::from)
            .toList());
    }
}
```

- [ ] **Step 4: Run the controller tests and verify they pass**

Run:

```bash
mvnw.cmd -pl decision-app -Dtest=WorkOrderControllerTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit the work-order REST API**

```bash
git add decision-app/src/main/java/com/ye/decision/controller/WorkOrderController.java decision-app/src/test/java/com/ye/decision/controller/WorkOrderControllerTest.java
git commit -m "feat(workorder): add frontend rest controller"
```

---

## Task 4: Add the shared frontend HTTP layer and SSE parser

**Files:**
- Create: `decision-web/src/types/api.ts`
- Create: `decision-web/src/types/chat.ts`
- Create: `decision-web/src/types/knowledge.ts`
- Create: `decision-web/src/types/tickets.ts`
- Create: `decision-web/src/api/http.ts`
- Create: `decision-web/src/api/chat.ts`
- Create: `decision-web/src/api/knowledge.ts`
- Create: `decision-web/src/api/tickets.ts`
- Create: `decision-web/src/utils/sse.ts`
- Create: `decision-web/src/utils/extractors.ts`
- Create: `decision-web/src/utils/sse.spec.ts`

- [ ] **Step 1: Write the failing SSE parser tests**

Create `decision-web/src/utils/sse.spec.ts`:

```ts
import { describe, expect, it } from 'vitest';

import { parseSseChunk } from './sse';

describe('parseSseChunk', () => {
  it('returns complete events and preserves tail buffer', () => {
    const result = parseSseChunk(
      'event:thought\\ndata:需要先查订单\\n\\n' +
      'event:answer\\ndata:最终回复',
      ''
    );

    expect(result.events).toEqual([
      { event: 'thought', data: '需要先查订单' },
    ]);
    expect(result.remainder).toBe('event:answer\\ndata:最终回复');
  });

  it('supports multi-line data payloads', () => {
    const result = parseSseChunk(
      'event:observation\\ndata:{"a":1}\\ndata:{"b":2}\\n\\n',
      ''
    );

    expect(result.events[0]).toEqual({
      event: 'observation',
      data: '{"a":1}\\n{"b":2}',
    });
  });
});
```

- [ ] **Step 2: Run the parser tests and verify they fail**

Run:

```bash
cd decision-web
npm run test -- src/utils/sse.spec.ts
```

Expected:

```text
Error: Cannot find module './sse'
```

- [ ] **Step 3: Implement the shared API client and parser**

Create `decision-web/src/types/api.ts`:

```ts
export interface ResultEnvelope<T> {
  code: number;
  msg: string;
  data: T;
}
```

Create `decision-web/src/types/chat.ts`:

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
```

Create `decision-web/src/types/tickets.ts`:

```ts
export type TicketStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED';
export type TicketType = 'ORDER' | 'LOGISTICS' | 'ACCOUNT' | 'TECH_FAULT' | 'CONSULTATION' | 'OTHER';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Ticket {
  orderNo: string;
  type: TicketType;
  priority: TicketPriority;
  status: TicketStatus;
  title: string;
  description: string;
  customerId: string;
  assignee: string | null;
  assigneeGroup: string | null;
  resolution: string | null;
  sessionId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  resolvedAt: string | null;
}

export interface TicketLog {
  action: string;
  operator: string;
  content: string;
  createdAt: string | null;
}
```

Create `decision-web/src/api/http.ts`:

```ts
import type { ResultEnvelope } from '@/types/api';

export async function requestJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const payload = (await response.json()) as ResultEnvelope<T>;
  if (payload.code !== 200) {
    throw new Error(payload.msg);
  }

  return payload.data;
}
```

Create `decision-web/src/utils/sse.ts`:

```ts
import type { ChatStreamEvent } from '@/types/chat';

export interface ParseResult {
  events: ChatStreamEvent[];
  remainder: string;
}

export function parseSseChunk(chunk: string, buffer = ''): ParseResult {
  const combined = buffer + chunk;
  const blocks = combined.split('\n\n');
  const remainder = blocks.pop() ?? '';
  const events = blocks
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => {
      const lines = block.split('\n');
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message';
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n');

      return {
        event: event as ChatStreamEvent['event'],
        data,
      };
    });

  return { events, remainder };
}
```

Create `decision-web/src/api/chat.ts`:

```ts
import type { ChatRequest, ChatStreamEvent } from '@/types/chat';
import { parseSseChunk } from '@/utils/sse';

export async function streamChat(request: ChatRequest, onEvent: (event: ChatStreamEvent) => void) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok || !response.body) {
    throw new Error(`聊天请求失败: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    const text = decoder.decode(value, { stream: true });
    const parsed = parseSseChunk(text, buffer);
    buffer = parsed.remainder;
    parsed.events.forEach(onEvent);
  }
}
```

Create `decision-web/src/types/knowledge.ts`:

```ts
export interface KnowledgeBase {
  kbCode: string;
  kbName: string;
  description: string;
  owner: string;
  status: number;
}

export interface KnowledgeDocument {
  docId: string;
  fileName: string;
  status: string;
}
```

Create `decision-web/src/api/tickets.ts`:

```ts
import type { Ticket, TicketLog, TicketPriority, TicketStatus, TicketType } from '@/types/tickets';
import { requestJson } from './http';

export interface TicketQuery {
  orderNo?: string;
  customerId?: string;
  status?: TicketStatus | '';
  type?: TicketType | '';
  priority?: TicketPriority | '';
}

export interface TicketCreatePayload {
  type: TicketType;
  priority?: TicketPriority;
  title: string;
  description: string;
  customerId: string;
  sessionId?: string;
}

export async function listTickets(query: TicketQuery) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });
  return requestJson<Ticket[]>(`/api/work-orders?${params.toString()}`);
}

export function createTicket(payload: TicketCreatePayload) {
  return requestJson<Ticket>('/api/work-orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function getTicket(orderNo: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}`);
}

export function getTicketLogs(orderNo: string) {
  return requestJson<TicketLog[]>(`/api/work-orders/${orderNo}/logs`);
}

export function updateTicketStatus(orderNo: string, status: TicketStatus, note: string, operator: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, note, operator }),
  });
}

export function closeTicket(orderNo: string, resolution: string, operator: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}/close`, {
    method: 'POST',
    body: JSON.stringify({ resolution, operator }),
  });
}
```

Create `decision-web/src/api/knowledge.ts`:

```ts
import { requestJson } from './http';
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';

export function listKnowledgeBases() {
  return requestJson<KnowledgeBase[]>('/api/kb');
}

export function getKnowledgeDocuments(kbCode: string) {
  return requestJson<{ records: KnowledgeDocument[] }>(`/api/kb/${kbCode}/documents`);
}

export function getDocumentStatus(kbCode: string, docId: string) {
  return requestJson<KnowledgeDocument>(`/api/kb/${kbCode}/documents/${docId}/status`);
}

export async function uploadDocument(kbCode: string, file: File, uploadedBy = 'console') {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('uploadedBy', uploadedBy);

  const response = await fetch(`/api/kb/${kbCode}/documents`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`上传失败: ${response.status}`);
  }

  const payload = await response.json();
  if (payload.code !== 200) {
    throw new Error(payload.msg);
  }

  return payload.data;
}
```

Create `decision-web/src/utils/extractors.ts`:

```ts
export function extractOrderNo(input: string) {
  return input.match(/WO\d{11}/)?.[0] ?? '';
}
```

- [ ] **Step 4: Run the frontend unit tests to verify the parser passes**

Run:

```bash
cd decision-web
npm run test -- src/utils/sse.spec.ts src/layouts/AppShell.spec.ts
```

Expected:

```text
✓ src/utils/sse.spec.ts
✓ src/layouts/AppShell.spec.ts
```

- [ ] **Step 5: Commit the shared frontend data layer**

```bash
git add decision-web/src/types decision-web/src/api decision-web/src/utils decision-web/src/utils/sse.spec.ts
git commit -m "feat(frontend): add shared api client and sse parser"
```

---

## Task 5: Build the workspace store and workspace page

**Files:**
- Create: `decision-web/src/stores/workspace.ts`
- Create: `decision-web/src/stores/workspace.spec.ts`
- Create: `decision-web/src/components/workspace/SessionRail.vue`
- Create: `decision-web/src/components/workspace/ChatTimeline.vue`
- Create: `decision-web/src/components/workspace/ContextPanel.vue`
- Create: `decision-web/src/components/workspace/ComposerBar.vue`
- Modify: `decision-web/src/views/WorkspaceView.vue`
- Modify: `decision-web/src/styles/theme.css`

- [ ] **Step 1: Write the failing workspace store test**

Create `decision-web/src/stores/workspace.spec.ts`:

```ts
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/chat', () => ({
  streamChat: vi.fn(async (_req, onEvent) => {
    onEvent({ event: 'thought', data: '需要查询物流' });
    onEvent({ event: 'action', data: 'callExternalApiTool | {"service":"logistics"}' });
    onEvent({ event: 'answer', data: '物流已更新，已创建工单 WO20260409001' });
    onEvent({ event: 'done', data: '[DONE]' });
  }),
}));

import { useWorkspaceStore } from './workspace';

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('streams chat events into the active session timeline', async () => {
    const store = useWorkspaceStore();
    await store.sendMessage('客户投诉物流慢');

    expect(store.activeSession.events).toHaveLength(5);
    expect(store.activeSession.events.at(-1)?.type).toBe('done');
    expect(store.context.ticketOrderNo).toBe('WO20260409001');
  });
});
```

- [ ] **Step 2: Run the store test and verify it fails**

Run:

```bash
cd decision-web
npm run test -- src/stores/workspace.spec.ts
```

Expected:

```text
Error: Cannot find module './workspace'
```

- [ ] **Step 3: Implement the workspace store and page**

Create `decision-web/src/stores/workspace.ts`:

```ts
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

export const useWorkspaceStore = defineStore('workspace', {
  state: () => ({
    sessions: [
      { id: crypto.randomUUID(), title: '新会话', events: [] as TimelineEvent[] },
    ] as SessionState[],
    activeSessionId: '',
    sending: false,
    context: {
      ticketOrderNo: '',
      activeTab: 'ticket',
    },
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

      this.sending = false;
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
    },
  },
});
```

Modify `decision-web/src/views/WorkspaceView.vue`:

```vue
<script setup lang="ts">
import { onMounted } from 'vue';

import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import ContextPanel from '@/components/workspace/ContextPanel.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();

onMounted(() => {
  store.bootstrap();
});
</script>

<template>
  <section class="workspace">
    <SessionRail
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="store.activateSession"
    />
    <div class="workspace__center">
      <header class="workspace__header">
        <p class="page__eyebrow">智能客服</p>
        <h1>对话工作台</h1>
      </header>
      <ChatTimeline :events="store.activeSession.events" />
      <ComposerBar :busy="store.sending" @submit="store.sendMessage" />
    </div>
    <ContextPanel :context="store.context" @create="store.createTicketFromContext" />
  </section>
</template>
```

Create `decision-web/src/components/workspace/ComposerBar.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{ submit: [message: string] }>();

const value = ref('');

function submit() {
  if (!value.value.trim() || props.busy) {
    return;
  }
  emit('submit', value.value.trim());
  value.value = '';
}
</script>

<template>
  <form class="composer" @submit.prevent="submit">
    <textarea v-model="value" class="composer__input" placeholder="输入客户诉求或问题..." />
    <button class="composer__button" :disabled="busy">
      {{ busy ? '处理中...' : '发送' }}
    </button>
  </form>
</template>
```

Create `decision-web/src/components/workspace/ContextPanel.vue`:

```vue
<script setup lang="ts">
import { reactive } from 'vue';

defineProps<{
  context: {
    ticketOrderNo: string;
    activeTab: string;
  };
}>();

const emit = defineEmits<{
  create: [payload: { type: 'LOGISTICS'; title: string; description: string; customerId: string; priority: 'HIGH' }];
}>();

const draft = reactive({
  customerId: '',
  title: '',
  description: '',
});

function submit() {
  if (!draft.customerId || !draft.title || !draft.description) {
    return;
  }

  emit('create', {
    type: 'LOGISTICS',
    title: draft.title,
    description: draft.description,
    customerId: draft.customerId,
    priority: 'HIGH',
  });
}
</script>

<template>
  <aside class="context-panel">
    <div class="context-panel__header">
      <p class="page__eyebrow">上下文</p>
      <h2>工单联动</h2>
    </div>

    <p v-if="context.ticketOrderNo">当前工单：{{ context.ticketOrderNo }}</p>

    <div class="context-panel__draft">
      <input v-model="draft.customerId" placeholder="客户 ID" />
      <input v-model="draft.title" placeholder="工单标题" />
      <textarea v-model="draft.description" placeholder="工单描述" />
      <button type="button" @click="submit">手动创建工单</button>
    </div>
  </aside>
</template>
```

Create `decision-web/src/components/workspace/SessionRail.vue`:

```vue
<script setup lang="ts">
defineProps<{
  sessions: Array<{ id: string; title: string }>;
  activeSessionId: string;
}>();

const emit = defineEmits<{ select: [sessionId: string] }>();
</script>

<template>
  <aside class="session-rail">
    <div class="session-rail__header">
      <p class="page__eyebrow">会话</p>
      <h2>最近会话</h2>
    </div>
    <button
      v-for="session in sessions"
      :key="session.id"
      class="session-rail__item"
      :data-active="session.id === activeSessionId"
      type="button"
      @click="emit('select', session.id)"
    >
      {{ session.title }}
    </button>
  </aside>
</template>
```

Create `decision-web/src/components/workspace/ChatTimeline.vue`:

```vue
<script setup lang="ts">
defineProps<{
  events: Array<{ id: string; type: string; content: string }>;
}>();
</script>

<template>
  <div class="chat-timeline">
    <article
      v-for="event in events"
      :key="event.id"
      class="chat-timeline__event"
      :data-type="event.type"
    >
      <p class="chat-timeline__type">{{ event.type }}</p>
      <p class="chat-timeline__content">{{ event.content }}</p>
    </article>
  </div>
</template>
```

- [ ] **Step 4: Run the workspace tests**

Run:

```bash
cd decision-web
npm run test -- src/stores/workspace.spec.ts
```

Expected:

```text
✓ src/stores/workspace.spec.ts
```

- [ ] **Step 5: Commit the workspace feature**

```bash
git add decision-web/src/stores/workspace.ts decision-web/src/stores/workspace.spec.ts decision-web/src/views/WorkspaceView.vue decision-web/src/components/workspace decision-web/src/styles/theme.css
git commit -m "feat(frontend): build workspace chat console"
```

---

## Task 6: Build the tickets page and ticket store

**Files:**
- Create: `decision-web/src/stores/tickets.ts`
- Create: `decision-web/src/stores/tickets.spec.ts`
- Create: `decision-web/src/components/tickets/TicketFilters.vue`
- Create: `decision-web/src/components/tickets/TicketList.vue`
- Create: `decision-web/src/components/tickets/TicketDetailPanel.vue`
- Modify: `decision-web/src/api/tickets.ts`
- Modify: `decision-web/src/views/TicketsView.vue`

- [ ] **Step 1: Write the failing ticket store test**

Create `decision-web/src/stores/tickets.spec.ts`:

```ts
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/tickets', () => ({
  listTickets: vi.fn(async () => [
    {
      orderNo: 'WO20260409001',
      type: 'LOGISTICS',
      priority: 'HIGH',
      status: 'PENDING',
      title: '物流延迟',
      description: '三天未更新',
      customerId: '13800001111',
      assignee: '物流专员',
      assigneeGroup: '物流组',
      resolution: null,
      sessionId: 'session-1',
      createdAt: '2026-04-09T10:00:00',
      updatedAt: '2026-04-09T10:00:00',
      resolvedAt: null
    }
  ]),
  getTicket: vi.fn(async (orderNo: string) => ({
    orderNo,
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'PENDING',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: null,
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:00:00',
    resolvedAt: null
  })),
  getTicketLogs: vi.fn(async () => [
    { action: 'CREATE', operator: 'agent', content: '创建工单', createdAt: '2026-04-09T10:00:00' }
  ]),
  updateTicketStatus: vi.fn(async () => ({
    orderNo: 'WO20260409001',
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'PROCESSING',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: null,
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:05:00',
    resolvedAt: null
  })),
  closeTicket: vi.fn(async () => ({
    orderNo: 'WO20260409001',
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'CLOSED',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: '已补发',
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:10:00',
    resolvedAt: '2026-04-09T10:10:00'
  }))
}));

import { useTicketsStore } from './tickets';

describe('tickets store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads ticket list and selected ticket details', async () => {
    const store = useTicketsStore();
    await store.loadTickets();
    await store.selectTicket('WO20260409001');

    expect(store.items).toHaveLength(1);
    expect(store.selected?.orderNo).toBe('WO20260409001');
    expect(store.logs).toHaveLength(1);
  });

  it('updates and closes the selected ticket', async () => {
    const store = useTicketsStore();
    await store.loadTickets();
    await store.selectTicket('WO20260409001');
    await store.updateSelectedStatus('PROCESSING', '开始处理', 'agent');
    expect(store.selected?.status).toBe('PROCESSING');

    await store.closeSelected('已补发', 'agent');
    expect(store.selected?.status).toBe('CLOSED');
  });
});
```

- [ ] **Step 2: Run the ticket store test and verify it fails**

Run:

```bash
cd decision-web
npm run test -- src/stores/tickets.spec.ts
```

Expected:

```text
Error: Cannot find module './tickets'
```

- [ ] **Step 3: Implement the ticket store and page**

Create `decision-web/src/stores/tickets.ts`:

```ts
import { defineStore } from 'pinia';

import { closeTicket, getTicket, getTicketLogs, listTickets, updateTicketStatus } from '@/api/tickets';
import type { Ticket, TicketLog } from '@/types/tickets';

export const useTicketsStore = defineStore('tickets', {
  state: () => ({
    filters: {
      orderNo: '',
      customerId: '',
      status: '',
      type: '',
      priority: '',
    },
    items: [] as Ticket[],
    selected: null as Ticket | null,
    logs: [] as TicketLog[],
    loading: false,
  }),
  actions: {
    async applyFilters(query: { orderNo: string; customerId: string; status: string; type: string; priority: string }) {
      this.filters = { ...query };
      await this.loadTickets();
    },
    async loadTickets() {
      this.loading = true;
      this.items = await listTickets(this.filters);
      this.loading = false;
      if (!this.selected && this.items[0]) {
        await this.selectTicket(this.items[0].orderNo);
      }
    },
    async selectTicket(orderNo: string) {
      const [ticket, logs] = await Promise.all([getTicket(orderNo), getTicketLogs(orderNo)]);
      this.selected = ticket;
      this.logs = logs;
    },
    async updateSelectedStatus(status: Ticket['status'], note: string, operator: string) {
      if (!this.selected) {
        return;
      }
      this.selected = await updateTicketStatus(this.selected.orderNo, status, note, operator);
      this.logs = await getTicketLogs(this.selected.orderNo);
    },
    async closeSelected(resolution: string, operator: string) {
      if (!this.selected) {
        return;
      }
      this.selected = await closeTicket(this.selected.orderNo, resolution, operator);
      this.logs = await getTicketLogs(this.selected.orderNo);
    },
  },
});
```

Modify `decision-web/src/views/TicketsView.vue`:

```vue
<script setup lang="ts">
import { onMounted } from 'vue';

import TicketDetailPanel from '@/components/tickets/TicketDetailPanel.vue';
import TicketFilters from '@/components/tickets/TicketFilters.vue';
import TicketList from '@/components/tickets/TicketList.vue';
import { useTicketsStore } from '@/stores/tickets';

const store = useTicketsStore();

onMounted(() => {
  store.loadTickets();
});
</script>

<template>
  <section class="tickets-page">
    <header class="page__header">
      <p class="page__eyebrow">Service</p>
      <h1>工单管理</h1>
    </header>
    <TicketFilters :filters="store.filters" @refresh="store.applyFilters" />
    <div class="tickets-page__body">
      <TicketList
        :items="store.items"
        :selected-order-no="store.selected?.orderNo ?? ''"
        @select="store.selectTicket"
      />
      <TicketDetailPanel
        :ticket="store.selected"
        :logs="store.logs"
        @update-status="store.updateSelectedStatus"
        @close="store.closeSelected"
      />
    </div>
  </section>
</template>
```

Create `decision-web/src/components/tickets/TicketFilters.vue`:

```vue
<script setup lang="ts">
const props = defineProps<{
  filters: {
    orderNo: string;
    customerId: string;
    status: string;
    type: string;
    priority: string;
  };
}>();

import { reactive } from 'vue';

const emit = defineEmits<{
  refresh: [query: { orderNo: string; customerId: string; status: string; type: string; priority: string }];
}>();

const query = reactive({ ...props.filters });
</script>

<template>
  <div class="ticket-filters">
    <input v-model="query.orderNo" placeholder="工单编号" />
    <input v-model="query.customerId" placeholder="客户 ID" />
    <button type="button" @click="emit('refresh', { ...query })">刷新</button>
  </div>
</template>
```

Create `decision-web/src/components/tickets/TicketList.vue`:

```vue
<script setup lang="ts">
defineProps<{
  items: Array<{ orderNo: string; title: string; status: string; priority: string }>;
  selectedOrderNo: string;
}>();

const emit = defineEmits<{ select: [orderNo: string] }>();
</script>

<template>
  <section class="ticket-list">
    <button
      v-for="ticket in items"
      :key="ticket.orderNo"
      type="button"
      class="ticket-list__item"
      :data-active="ticket.orderNo === selectedOrderNo"
      @click="emit('select', ticket.orderNo)"
    >
      <strong>{{ ticket.orderNo }}</strong>
      <span>{{ ticket.title }}</span>
      <span>{{ ticket.status }} / {{ ticket.priority }}</span>
    </button>
  </section>
</template>
```

Create `decision-web/src/components/tickets/TicketDetailPanel.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue';

defineProps<{
  ticket: null | { orderNo: string; title: string; description: string; status: string; assignee: string | null };
  logs: Array<{ action: string; operator: string; content: string }>;
}>();

const emit = defineEmits<{
  updateStatus: [status: 'PROCESSING' | 'RESOLVED', note: string, operator: string];
  close: [resolution: string, operator: string];
}>();

const note = ref('');
const resolution = ref('');
</script>

<template>
  <aside class="ticket-detail">
    <template v-if="ticket">
      <h2>{{ ticket.orderNo }}</h2>
      <p>{{ ticket.title }}</p>
      <p>{{ ticket.description }}</p>
      <p>状态：{{ ticket.status }}</p>
      <p>处理人：{{ ticket.assignee ?? '未分配' }}</p>

      <div class="ticket-detail__actions">
        <textarea v-model="note" placeholder="处理备注" />
        <button type="button" @click="emit('updateStatus', 'PROCESSING', note, 'console')">标记处理中</button>
        <button type="button" @click="emit('updateStatus', 'RESOLVED', note, 'console')">标记已解决</button>
        <textarea v-model="resolution" placeholder="关闭说明" />
        <button type="button" @click="emit('close', resolution, 'console')">关闭工单</button>
      </div>

      <div class="ticket-detail__logs">
        <article v-for="log in logs" :key="`${log.action}-${log.content}`">
          <strong>{{ log.action }}</strong>
          <span>{{ log.operator }}</span>
          <p>{{ log.content }}</p>
        </article>
      </div>
    </template>
  </aside>
</template>
```

- [ ] **Step 4: Run the ticket store test**

Run:

```bash
cd decision-web
npm run test -- src/stores/tickets.spec.ts
```

Expected:

```text
✓ src/stores/tickets.spec.ts
```

- [ ] **Step 5: Commit the tickets feature**

```bash
git add decision-web/src/stores/tickets.ts decision-web/src/stores/tickets.spec.ts decision-web/src/views/TicketsView.vue decision-web/src/components/tickets
git commit -m "feat(frontend): add ticket management page"
```

---

## Task 7: Build the knowledge page and upload-status polling

**Files:**
- Create: `decision-web/src/stores/knowledge.ts`
- Create: `decision-web/src/stores/knowledge.spec.ts`
- Create: `decision-web/src/components/knowledge/KnowledgeSidebar.vue`
- Create: `decision-web/src/components/knowledge/KnowledgeDocumentTable.vue`
- Modify: `decision-web/src/views/KnowledgeView.vue`

- [ ] **Step 1: Write the failing knowledge store test**

Create `decision-web/src/stores/knowledge.spec.ts`:

```ts
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBases: vi.fn(async () => [
    {
      kbCode: 'product-docs',
      kbName: '产品文档库',
      description: '产品说明',
      owner: 'tech-team',
      status: 1
    }
  ]),
  getKnowledgeDocuments: vi.fn(async () => ({
    records: [
      {
        docId: 'doc-1',
        fileName: 'guide.pdf',
        status: 'PROCESSING'
      }
    ]
  })),
  getDocumentStatus: vi.fn(async () => ({
    docId: 'doc-1',
    fileName: 'guide.pdf',
    status: 'COMPLETED'
  }))
}));

import { useKnowledgeStore } from './knowledge';

describe('knowledge store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads knowledge bases and documents for the active base', async () => {
    const store = useKnowledgeStore();
    await store.loadBases();
    await store.selectBase('product-docs');

    expect(store.bases[0].kbCode).toBe('product-docs');
    expect(store.documents[0].status).toBe('PROCESSING');
  });

  it('refreshes a document status after polling', async () => {
    const store = useKnowledgeStore();
    await store.loadBases();
    await store.selectBase('product-docs');
    await store.refreshDocumentStatus('doc-1');

    expect(store.documents[0].status).toBe('COMPLETED');
  });
});
```

- [ ] **Step 2: Run the knowledge store test and verify it fails**

Run:

```bash
cd decision-web
npm run test -- src/stores/knowledge.spec.ts
```

Expected:

```text
Error: Cannot find module './knowledge'
```

- [ ] **Step 3: Implement the knowledge store and page**

Modify `decision-web/src/types/knowledge.ts`:

```ts
export interface KnowledgeBase {
  kbCode: string;
  kbName: string;
  description: string;
  owner: string;
  status: number;
}

export interface KnowledgeDocument {
  docId: string;
  fileName: string;
  status: string;
}
```

Create `decision-web/src/stores/knowledge.ts`:

```ts
import { defineStore } from 'pinia';

import { getDocumentStatus, getKnowledgeDocuments, listKnowledgeBases, uploadDocument } from '@/api/knowledge';
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';

export const useKnowledgeStore = defineStore('knowledge', {
  state: () => ({
    bases: [] as KnowledgeBase[],
    activeKbCode: '',
    documents: [] as KnowledgeDocument[],
    loading: false,
  }),
  actions: {
    async loadBases() {
      this.loading = true;
      this.bases = await listKnowledgeBases();
      this.loading = false;
      if (!this.activeKbCode && this.bases[0]) {
        this.activeKbCode = this.bases[0].kbCode;
      }
    },
    async selectBase(kbCode: string) {
      this.activeKbCode = kbCode;
      const page = await getKnowledgeDocuments(kbCode);
      this.documents = page.records;
    },
    async uploadToActiveBase(file: File) {
      if (!this.activeKbCode) {
        return;
      }
      await uploadDocument(this.activeKbCode, file);
      await this.selectBase(this.activeKbCode);
    },
    async refreshDocumentStatus(docId: string) {
      if (!this.activeKbCode) {
        return;
      }
      const latest = await getDocumentStatus(this.activeKbCode, docId);
      this.documents = this.documents.map((doc) => (doc.docId === docId ? latest : doc));
    },
  },
});
```

Modify `decision-web/src/views/KnowledgeView.vue`:

```vue
<script setup lang="ts">
import { onMounted } from 'vue';

import KnowledgeDocumentTable from '@/components/knowledge/KnowledgeDocumentTable.vue';
import KnowledgeSidebar from '@/components/knowledge/KnowledgeSidebar.vue';
import { useKnowledgeStore } from '@/stores/knowledge';

const store = useKnowledgeStore();

onMounted(async () => {
  await store.loadBases();
  if (store.activeKbCode) {
    await store.selectBase(store.activeKbCode);
  }
});
</script>

<template>
  <section class="knowledge-page">
    <header class="page__header">
      <p class="page__eyebrow">RAG</p>
      <h1>知识库管理</h1>
    </header>
    <div class="knowledge-page__body">
      <KnowledgeSidebar
        :bases="store.bases"
        :active-kb-code="store.activeKbCode"
        @select="store.selectBase"
      />
      <KnowledgeDocumentTable
        :kb-code="store.activeKbCode"
        :documents="store.documents"
        @upload="store.uploadToActiveBase"
      />
    </div>
  </section>
</template>
```

Create `decision-web/src/components/knowledge/KnowledgeSidebar.vue`:

```vue
<script setup lang="ts">
defineProps<{
  bases: Array<{ kbCode: string; kbName: string }>;
  activeKbCode: string;
}>();

const emit = defineEmits<{ select: [kbCode: string] }>();
</script>

<template>
  <aside class="knowledge-sidebar">
    <button
      v-for="base in bases"
      :key="base.kbCode"
      type="button"
      :data-active="base.kbCode === activeKbCode"
      @click="emit('select', base.kbCode)"
    >
      {{ base.kbName }}
    </button>
  </aside>
</template>
```

Create `decision-web/src/components/knowledge/KnowledgeDocumentTable.vue`:

```vue
<script setup lang="ts">
const emit = defineEmits<{ upload: [file: File] }>();

defineProps<{
  kbCode: string;
  documents: Array<{ docId: string; fileName: string; status: string }>;
}>();

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('upload', file);
  }
}
</script>

<template>
  <section class="knowledge-documents">
    <header>
      <h2>{{ kbCode || '未选择知识库' }}</h2>
      <input type="file" @change="onFileChange" />
    </header>
    <article v-for="document in documents" :key="document.docId" class="knowledge-documents__row">
      <strong>{{ document.fileName }}</strong>
      <span>{{ document.status }}</span>
    </article>
  </section>
</template>
```

- [ ] **Step 4: Run the knowledge store test**

Run:

```bash
cd decision-web
npm run test -- src/stores/knowledge.spec.ts
```

Expected:

```text
✓ src/stores/knowledge.spec.ts
```

- [ ] **Step 5: Commit the knowledge feature**

```bash
git add decision-web/src/types/knowledge.ts decision-web/src/api/knowledge.ts decision-web/src/stores/knowledge.ts decision-web/src/stores/knowledge.spec.ts decision-web/src/views/KnowledgeView.vue decision-web/src/components/knowledge
git commit -m "feat(frontend): add knowledge management page"
```

---

## Task 8: Add responsive polish, end-to-end smoke tests, and final verification

**Files:**
- Create: `decision-web/playwright.config.ts`
- Create: `decision-web/tests/e2e/console.spec.ts`
- Modify: `decision-web/src/styles/theme.css`
- Modify: `decision-web/src/views/WorkspaceView.vue`
- Modify: `decision-web/src/views/TicketsView.vue`
- Modify: `decision-web/src/views/KnowledgeView.vue`

- [ ] **Step 1: Write the failing Playwright smoke test**

Create `decision-web/tests/e2e/console.spec.ts`:

```ts
import { expect, test } from '@playwright/test';

test('workspace, tickets, and knowledge pages all render shell navigation', async ({ page }) => {
  await page.goto('/workspace');
  await expect(page.getByRole('link', { name: '工作台' })).toBeVisible();

  await page.getByRole('link', { name: '工单' }).click();
  await expect(page.getByRole('heading', { name: '工单管理' })).toBeVisible();

  await page.getByRole('link', { name: '知识库' }).click();
  await expect(page.getByRole('heading', { name: '知识库管理' })).toBeVisible();
});
```

- [ ] **Step 2: Run the E2E test and verify it fails because Playwright is not configured**

Run:

```bash
cd decision-web
npm run test:e2e -- tests/e2e/console.spec.ts
```

Expected:

```text
Error: Playwright Test did not expect test() to be called here
```

- [ ] **Step 3: Add Playwright config and responsive CSS polish**

Create `decision-web/playwright.config.ts`:

```ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    headless: true,
  },
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4173',
    port: 4173,
    reuseExistingServer: !process.env.CI,
  },
});
```

Append to `decision-web/src/styles/theme.css`:

```css
.workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 360px;
  gap: 20px;
}

.workspace__center,
.tickets-page__body,
.knowledge-page__body {
  display: grid;
  gap: 16px;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page {
    padding: 18px;
    border-radius: 20px;
  }
}
```

- [ ] **Step 4: Run the full frontend test suite and backend work-order tests**

Run:

```bash
cd decision-web
npm run test
npm run test:e2e -- tests/e2e/console.spec.ts
cd ..
mvnw.cmd -pl decision-app -Dtest=WorkOrderServiceTest,WorkOrderControllerTest,ChatControllerTest test
```

Expected:

```text
Vitest: all tests passed
Playwright: 1 passed
BUILD SUCCESS
```

- [ ] **Step 5: Commit the polish and verification work**

```bash
git add decision-web/playwright.config.ts decision-web/tests/e2e/console.spec.ts decision-web/src/styles/theme.css decision-web/src/views/WorkspaceView.vue decision-web/src/views/TicketsView.vue decision-web/src/views/KnowledgeView.vue
git commit -m "test(frontend): add responsive polish and end-to-end smoke coverage"
```

---

## Self-Review

### Spec coverage

- `独立前端工程` -> Task 1
- `工作台 / 知识库 / 工单三页` -> Tasks 5, 6, 7
- `聊天 SSE 接入` -> Tasks 4, 5
- `新增工单 REST API` -> Tasks 2, 3
- `视觉与响应式` -> Tasks 1, 8
- `测试与验证` -> Tasks 1, 2, 3, 4, 5, 6, 7, 8

No spec gap remains.

### Placeholder scan

- No placeholder markers remain
- No deferred-test language remains
- Every task includes exact files, commands, and target code

### Type consistency

- Backend request/response names are fixed as `WorkOrderCreateReq`, `WorkOrderStatusUpdateReq`, `WorkOrderCloseReq`, `WorkOrderVO`, `WorkOrderLogVO`
- Frontend store names are fixed as `workspace`, `tickets`, `knowledge`
- Route names are fixed as `/workspace`, `/tickets`, `/knowledge`

No naming conflicts remain.
