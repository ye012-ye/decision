# 单 Agent 玻璃拟态聊天页 — 设计文档

- 日期：2026-06-06
- 模块：`decision-web`
- 状态：已确认，待生成实现计划

## 1. 目标与范围

把 `decision-web` 从「工作台 / 知识库 / 工单」三页 + 多 agent 路由展示的形态，精简为**单页、单一助手**的现代化聊天应用，视觉方向为**玻璃拟态 + 极光渐变氛围**。

后端 `LlmRoutingAgent → 5 域 ReactAgent` 不改动：前端只对接 `POST /api/chat/stream` 的 SSE 流，并把多 agent 行为**对外呈现为一个统一助手**（仍保留思考/工具过程作为「Agent 感」亮点）。

### 范围内
- 单页聊天 UI 重做（玻璃拟态 + 渐变）。
- 真正可用的多会话列表（新建 / 切换 / 删除）。
- 保留并美化 Agent 思考过程 / 工具调用迹象。
- 删除知识库、工单两条功能线的全部前端代码。
- 双主题（亮 / 暗 / 自动）下都有意设计的渐变。

### 范围外（不做）
- 后端任何改动。
- SSE 协议 / 事件名变更（保持与 `agent.stream.GraphEventAdapter` 的契约）。
- 新增「单页聊天」以外的功能（YAGNI）。
- 会话历史的服务端持久化（会话仍存于前端内存 / store，刷新即重置，与现状一致）。

## 2. 架构与组件

### 2.1 顶层结构

```
App.vue
└─ AppProviders.vue            (Naive UI provider 树，保留)
   └─ ChatLayout.vue           (新；替代 AppShell + TopBar + SidebarNav)
      ├─ BackgroundAurora.vue  (新；渐变画布 + 缓慢漂移的模糊光斑)
      ├─ SessionRail.vue       (重做为玻璃；品牌 + 新对话 + 历史列表 + 主题切换)
      └─ ChatPane             (聊天主区)
         ├─ 顶部细条           (会话标题 + 状态徽标)
         ├─ ChatTimeline.vue   (重做；居中阅读列 ~760px)
         │  ├─ EmptyState.vue  (重做；问候 + 玻璃建议气泡)
         │  └─ ChatMessage.vue (重做；用户右、助手左)
         │     └─ ChatProcessTrace.vue (重做为玻璃折叠卡)
         │        └─ ToolCallCard.vue  (重做)
         └─ ComposerBar.vue    (重做；玻璃胶囊输入 + 渐变发送键)
```

> 命名取舍：新增 `ChatLayout.vue` 承载单页布局，取代 `layouts/AppShell.vue`。`views/WorkspaceView.vue` 的职责并入 `ChatLayout`/`ChatPane`；为减少改动，单页根可继续由路由 `/` 指向一个轻量 `WorkspaceView.vue`（仅渲染 `ChatLayout`），或直接在 `App.vue` 渲染 `ChatLayout`。实现计划阶段二选一，默认：路由 `/` → `WorkspaceView.vue` → `ChatLayout`，保留路由层以便 404 兜底。

### 2.2 路由（`router/index.ts`）
- `/` → 聊天页
- `/:pathMatch(.*)*` → `NotFoundView`（保留）
- 删除 `/workspace`、`/knowledge`、`/tickets` 及对应 import。

### 2.3 状态（`stores/workspace.ts`）

保留 Options 风格 store。改动：

- **会话生命周期（真正实现）**：
  - `newConversation()`：新建空会话并激活（替换当前 `WorkspaceView` 里 `onCreateSession` 的占位 `$message`）。
  - `activateSession(id)`：保留。
  - `removeSession(id)`：删除会话；若删除的是当前会话则切到相邻会话；列表空时自动建一个空会话。
  - 首条用户消息发出后，把会话 `title` 由「新会话」更新为该消息的截断文本（如前 ~20 字）。
- **移除工单耦合**：删除 `createTicketFromContext`、`WorkspaceContext.ticketOrderNo / activeTab`、对 `extractOrderNo` 与 `@/api/tickets` 的依赖。`SessionState.context` 若无其他用途则一并移除。
- **SSE 流式与中断逻辑不变**：`sendMessage` / `stopStreaming` / `AbortController` / 事件分发（`route|thought|action|observation|answer|done|error`）保持现有行为，仅去掉 `updateTicketContextFromText` 调用。

`stores/theme.ts` 逻辑不变（`light|dark|auto`，写 `localStorage`，设 `documentElement.dataset.theme`）。

### 2.4 样式 token（`src/styles/tokens.css` + `src/theme/index.ts`）

> 约束：两文件颜色必须同步（见 `decision-web/CLAUDE.md`）。本次新增渐变/玻璃 token 也要两边对齐其中的纯色部分。

新增 token（示意，最终值在实现期定稿）：
- 极光渐变停靠色：紫 `#7c5cff` → 蓝 `#2563eb` → 青 `#22d3ee`（暗色降明度处理）。
- `--gradient-aurora`、`--color-accent`、`--color-accent-grad`（发送键/激活态/品牌用渐变）。
- `--glass-bg`、`--glass-border`、`--glass-blur`、`--glass-shadow`（亮 / 暗两套）。
- 双主题：亮色为浅色极光（紫蓝青低饱和），暗色为深底极光（深蓝紫底 + 强调光晕）。

## 3. 视觉规范

- **背景**：`BackgroundAurora` —— 全屏底层渐变 + 2~3 个大面积模糊径向光斑（紫 / 青 / 粉），低透明度，叠一层细噪声/磨砂以增质感。
- **玻璃卡片**：半透明背景 + `backdrop-filter: blur()` + 1px 高光内描边 + 柔和外投影；亮色用白玻璃，暗色用 `rgba(255,255,255,.06)` 玻璃。
- **消息**：用户消息右对齐、玻璃气泡 + 强调色描边/辉光；助手消息左对齐、玻璃面、Markdown 渲染（沿用 `utils/markdown.ts`，保留其 HTML 转义防 XSS）。
- **思考过程**：`ChatProcessTrace` 折叠卡玻璃化；流式 / 出错时自动展开（沿用现有逻辑）；`action` 的 `"toolName | arguments"` 解析不变。
- **层次**：通过尺度对比、留白节奏、玻璃叠加 + 光晕营造深度，避免均匀卡片网格的模板感。

## 4. 动效与性能

- 极光光斑缓慢漂移；消息进入淡入 + 轻微上浮；流式打字微光。
- 仅动画 `transform` / `opacity`（合成器友好），`will-change` 谨慎使用并及时移除。
- 全部动效尊重 `prefers-reduced-motion: reduce`（关闭漂移与进入动画）。
- 维持轻量：不引入新的重型动画库，优先 CSS。

## 5. 无障碍

- 语义化结构（`header` / `main` / `nav` / 列表）。
- 输入框、发送 / 停止、会话项、主题切换均有可见的 hover / focus / active 态与键盘可达性。
- 玻璃背景上的文字保证对比度达标。

## 6. 删除清单（知识库 / 工单整条线 + 已废弃布局件）

- 视图：`views/KnowledgeView.vue`、`views/TicketsView.vue`
- 组件：`components/knowledge/*`（3 个）、`components/tickets/*`（3 个）、`components/workspace/ContextPanel.vue`、`components/common/SidebarNav.vue`
- 布局：`layouts/AppShell.vue`、`layouts/TopBar.vue`（职责并入 `ChatLayout`）
- store：`stores/knowledge.ts`(+spec)、`stores/tickets.ts`(+spec)
- api：`api/knowledge.ts`、`api/tickets.ts`
- 类型：`types/knowledge.ts`、`types/tickets.ts`
- 工具：`utils/extractors.ts`（仅服务于工单号提取）

> 删除后需清理所有对上述文件的 import（`router`、`workspace` store、`WorkspaceView`、相关 spec）。`api/http.ts`（`requestJson` / `ResultEnvelope`）保留——聊天虽走原始流，但工具基础设施保留以备后用；若删除后再无引用，则在实现期一并移除并记录。

## 7. 测试策略

- **保持不动**：`utils/sse.spec.ts`、`utils/markdown.spec.ts`、`stores/theme.spec.ts`。
- **更新**：
  - `stores/workspace.spec.ts`：删除工单相关用例；新增「新建 / 切换 / 删除会话」「首条消息生成标题」用例；保留并核对 SSE 事件分发用例。
  - `components/workspace/ChatTimeline.spec.ts`、`ComposerBar.spec.ts`：适配重做后的结构与 `data-testid`。
  - `views/WorkspaceView.spec.ts`：适配单页结构（或迁移为 `ChatLayout` 的测试）。
  - e2e `tests/e2e/{smoke,theme-toggle,console}.spec.ts`：适配新布局（去掉对 `/knowledge`、`/tickets` 的访问），保持全绿；`console.spec` 仍校验无意外控制台报错。
- **删除**：`stores/knowledge.spec.ts`、`stores/tickets.spec.ts`。
- **新增（视情况）**：`ChatProcessTrace` 步骤配对（action+observation）单测若现无覆盖则补。
- **验收门槛**：`npm run build`（含 `vue-tsc` 类型检查）通过；`npm run test` 全绿；`npm run test:e2e` 全绿。

## 8. 风险与缓解

- **删除范围较大**：先确保所有 import 清理干净，靠 `vue-tsc` 类型检查兜底找残留引用。
- **玻璃性能**：`backdrop-filter` 在低端设备有开销；限制同屏玻璃层数量与模糊半径，长列表只在可视容器上模糊。
- **token 双写漂移**：`tokens.css` 与 `theme/index.ts` 同步修改，并在 PR 自检。
- **e2e 易碎**：用确定性等待与 `data-testid`，不用定时假设。
