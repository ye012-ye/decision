# decision-web 前端现代化重构设计

- 日期：2026-04-11
- 范围：`decision-web/`（Vue 3 + Vite + TypeScript）
- 目标：结构 + 视觉大改，引入 Naive UI，建立亮色为主 + 可选暗色的主题体系，工作台深度升级，知识库/工单轻度跟随

## 1. 背景与决策

现状：单暗色主题，自研 CSS（`src/styles/theme.css` ~620 行），无 UI 组件库，三个主视图（工作台/知识库/工单），工作台以 ReAct SSE 流式（`thought/action/observation/answer`）为核心。

本次重构对齐的 7 项决策：

1. **改造范围**：结构 + 视觉大改
2. **UI 库**：引入 Naive UI
3. **主题基调**：亮色为主，提供暗色切换
4. **布局**：保持三栏 + 新增 TopBar
5. **工作台深度**：视觉升级 + ReAct 过程可视化 + 工具调用卡片 + Composer 升级
6. **知识库/工单**：轻度跟随（仅换皮 + 组件替换，不增功能）
7. **工程副产物**：CSS Token 化 + 样式文件拆分 + 主题 store（持久化 + 跟随系统）+ 图标库统一（`@vicons/ionicons5`）+ 全局 Message/Dialog/Notification Provider

**品牌色取舍**：亮色主色使用 `#2563eb`（蓝），暗色模式保留原品牌琥珀 `#f0aa52`。

## 2. 工程结构与依赖

### 2.1 新增依赖

- `naive-ui` ^2.x
- `@vicons/ionicons5` ^0.12.x
- `marked` + `highlight.js/lib/core`（markdown 渲染与按需代码高亮）

### 2.2 目录调整

```text
src/
├── styles/
│   ├── tokens.css        # 新：CSS 变量（亮/暗两套），单一真源
│   ├── reset.css         # 保留
│   └── layout.css        # 新：从 theme.css 拆出的布局类
├── theme/
│   ├── index.ts          # 新：naive lightOverrides / darkOverrides
│   └── icons.ts          # 新：图标统一 re-export
├── stores/
│   └── theme.ts          # 新：主题状态，持久化 + 跟随系统
├── providers/
│   ├── AppProviders.vue  # 新：NConfigProvider + Loading/Dialog/Notification/Message
│   └── MessageApiSetup.vue  # 新：把 $message/$dialog 挂到 window
├── layouts/
│   ├── AppShell.vue      # 改：补 TopBar + 响应式抽屉
│   └── TopBar.vue        # 新
├── components/
│   └── workspace/
│       ├── ChatMessage.vue         # 新
│       ├── ChatProcessTrace.vue    # 新
│       └── ToolCallCard.vue        # 新
└── ...
```

删除：`src/styles/theme.css`（内容迁移到 tokens/layout/各组件 scoped 样式）。

### 2.3 Token 设计

```css
:root {
  /* 亮色为默认 */
  --color-bg: #f7f8fa;
  --color-surface: #ffffff;
  --color-surface-sunken: #f0f2f5;
  --color-border: #e5e7eb;
  --color-text: #1f2329;
  --color-text-muted: #6b7280;
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-primary-pressed: #1e40af;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-danger:  #ef4444;

  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 24px;

  --shadow-sm: 0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.08);
  --shadow-md: 0 4px 12px rgba(16,24,40,.08);

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;
}

html[data-theme='dark'] {
  --color-bg: #0b0f17;
  --color-surface: #141a24;
  --color-surface-sunken: #0f141d;
  --color-border: #232a36;
  --color-text: #e6ebf2;
  --color-text-muted: #8a94a6;
  --color-primary: #f0aa52;
  --color-primary-hover: #e89a3a;
  --color-primary-pressed: #d68a2a;
  /* 其余同值或微调 */
}
```

### 2.4 `theme/index.ts`（Naive overrides）

```ts
// ⚠️ 此处颜色必须与 src/styles/tokens.css 保持一致
const radii = { small: '8px', medium: '12px', large: '16px' }
const light = { primary: '#2563eb', primaryHover: '#1d4ed8', primaryPressed: '#1e40af' }
const dark  = { primary: '#f0aa52', primaryHover: '#e89a3a', primaryPressed: '#d68a2a' }

export const lightOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: light.primary,
    primaryColorHover: light.primaryHover,
    primaryColorPressed: light.primaryPressed,
    borderRadius: radii.medium,
    fontFamily: '"Noto Sans SC","PingFang SC","Microsoft YaHei",system-ui,sans-serif',
  },
  Card:   { borderRadius: radii.large },
  Button: { borderRadiusTiny: radii.small, borderRadiusSmall: radii.small },
  // ...
}
export const darkOverrides: GlobalThemeOverrides = { /* 同结构，换 dark 变量 */ }
```

**取舍说明**：颜色在 `tokens.css` 和 `theme/index.ts` 各维护一份。相比运行时 `getComputedStyle` 读取 CSS 变量再构造 overrides，双份静态定义性能和时序都更可靠。文件顶部注释提示同步要求。

## 3. 主题 store

```ts
// stores/theme.ts
type Mode = 'light' | 'dark' | 'auto'

export const useThemeStore = defineStore('theme', {
  state: () => ({ mode: (localStorage.getItem('theme') as Mode) || 'auto' }),
  getters: {
    resolved(state): 'light' | 'dark' {
      if (state.mode !== 'auto') return state.mode
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    },
  },
  actions: {
    setMode(m: Mode) {
      this.mode = m
      localStorage.setItem('theme', m)
      document.documentElement.dataset.theme = this.resolved
    },
    init() {
      document.documentElement.dataset.theme = this.resolved
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (this.mode === 'auto') {
          document.documentElement.dataset.theme = this.resolved
        }
      })
    },
  },
})
```

`main.ts` 在 `createApp().use(pinia)` 之后调用 `useThemeStore().init()`。

## 4. AppProviders

```vue
<!-- providers/AppProviders.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider, NMessageProvider, NDialogProvider,
  NNotificationProvider, NLoadingBarProvider,
  darkTheme, lightTheme,
} from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { lightOverrides, darkOverrides } from '@/theme'
import MessageApiSetup from './MessageApiSetup.vue'

const theme = useThemeStore()
const naiveTheme = computed(() => theme.resolved === 'dark' ? darkTheme : lightTheme)
const overrides  = computed(() => theme.resolved === 'dark' ? darkOverrides : lightOverrides)
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

`MessageApiSetup.vue` 在 `onMounted` 中将 `useMessage()` / `useDialog()` / `useNotification()` / `useLoadingBar()` 挂到 `window.$message` 等全局，供 store / api 层非组件代码使用。`env.d.ts` 补对应类型声明。

## 5. AppShell + TopBar

### 5.1 布局

```text
┌─────────────────────────────────────────────────┐
│ TopBar  (56px sticky)                            │
├────────┬────────────────────────────────────────┤
│Sidebar │  RouterView                            │
│ 240px  │                                        │
└────────┴────────────────────────────────────────┘
```

### 5.2 TopBar 内容

- 左：Logo + 产品名（"决策中心"，沿用现有文案）；移动端为汉堡按钮
- 中：`NInput` 全局搜索（`Cmd/Ctrl+K` 快捷键占位，点击弹 NDialog 占位，无实际搜索逻辑）
- 右：
  - 主题切换：`NButton` quaternary + 图标，点击循环 `light → dark → auto`
  - 通知铃铛：`NBadge`（count 固定 0，占位）
  - 用户头像：`NAvatar` + `NDropdown` 菜单（个人资料 / 设置 / 退出，全部占位）

### 5.3 Sidebar 重写

- 宽度 240px（原 272）
- `NMenu` 替代原 `.sidebar__link`：图标 + 中文名，激活态 = `--color-primary` 10% 背景 + 左侧 3px primary 条
- 顶部品牌区不再重复 eyebrow（移至 TopBar）
- 底部：版本号 / 环境标签（muted 小字）

### 5.4 响应式

- `≥ 1200px`：默认三栏布局完整
- `< 1200px`：工作台右侧 ContextPanel 改为抽屉按钮触发
- `< 980px`：Sidebar 折叠成 `NDrawer` (placement=left)，TopBar 左侧显示汉堡
- `< 720px`：TopBar 搜索收起为图标按钮，点击展开搜索

### 5.5 AppShell 结构

```vue
<AppProviders>
  <div class="app-shell">
    <TopBar @toggle-sidebar="drawerOpen = true" />
    <div class="app-shell__body">
      <SidebarNav v-if="!isMobile" />
      <NDrawer v-else v-model:show="drawerOpen" :width="280" placement="left">
        <SidebarNav />
      </NDrawer>
      <main class="app-shell__main"><RouterView /></main>
    </div>
  </div>
</AppProviders>
```

## 6. 工作台（Workspace）重构

### 6.1 布局

```text
┌─ workspace ─────────────────────────────────────────────────┐
│ 页头: 标题 + NTag 状态徽章 + 操作区                            │
├────────────┬─────────────────────────────┬──────────────────┤
│ SessionRail│  ChatTimeline                │ ContextPanel     │
│ 272        │  (flex-1, 溢出滚动)            │ 340              │
│            │                              │                  │
│            │  ChatMessage（含 Trace）     │                  │
│            │  ...                         │                  │
│            │                              │                  │
│            ├─────────────────────────────┤                  │
│            │  ComposerBar (sticky)        │                  │
└────────────┴─────────────────────────────┴──────────────────┘
```

所有卡片：`background: var(--color-surface)` + `border-radius: var(--radius-xl)` + `border: 1px solid var(--color-border)` + `box-shadow: var(--shadow-sm)`。**去掉**径向渐变、backdrop-filter、玻璃态。

### 6.2 SessionRail

- 顶部：搜索 `NInput` + "新建会话" `NButton type=primary`
- 列表：自定义 div 项 + `NScrollbar` 包裹
- 激活项：`--color-primary` 10% 背景 + 左侧 3px 条 + 主色标题
- 项内容：标题（1 行省略）+ 时间（相对时间）+ 状态小点
- 右键：`NDropdown` 菜单（重命名 / 删除，占位）

### 6.3 ChatTimeline

组件变薄：只负责循环 + 空态 + 滚动。

**空态**：居中 SVG 插画（单色 `currentColor`）+ 文案"开始一段对话" + 3 个建议问题 chip（`NTag checkable`），点击塞入 composer。

**悬浮滚动到底按钮**：当用户向上滚动 > 200px 时显示，`NButton circle` + 图标；流式输出时保持自动跟随滚动，用户手动滚动则停止跟随。

### 6.4 ChatMessage

**用户消息**（右对齐，max-width 68%）：

- 右侧 `NAvatar`（字母或用户名首字母）
- 气泡：`--color-primary` 12% 背景 + 1px primary 30% 边 + `--radius-lg`
- 底部小字：时间 + 状态（已发送 / 失败重试）

**Assistant 消息**（左对齐，max-width 80%）：

- 左侧 `NAvatar`（Logo + 主色圆底）
- 顶部 `ChatProcessTrace`
- 正文气泡：`--color-surface-sunken` 背景 + 1px border
- 正文内容：marked + highlight.js 渲染；流式期末尾加 `▊` CSS blink 光标
- 底部操作条（`NButton quaternary size=tiny`）：复制 / 重新生成（占位）/ 点赞 / 点踩（占位）

### 6.5 ChatProcessTrace + ToolCallCard（核心升级）

将 SSE 的 `thought` / `action` / `observation` 组织成步骤时间线：

```text
┌─ 思考过程 (3 步 · 已完成)  [展开/折叠] ──────────┐
│ ① 💭 思考                                        │
│    用户在询问订单状态...                         │
│                                                  │
│ ② 🔧 调用工具 QueryMysqlTool · NTag(230ms)      │
│    ┌ 参数 ────────────┐                         │
│    │ { "sql": "..." } │  代码高亮                │
│    └──────────────────┘                         │
│    ┌ 结果 ────────────┐                         │
│    │ [...]            │                         │
│    └──────────────────┘                         │
│                                                  │
│ ③ 💭 思考 → 生成回复                             │
└──────────────────────────────────────────────────┘
```

- 外层 `NCollapse`：折叠态仅显示"思考过程 (N 步) + 步数徽章 + 总耗时"
- **流式中（`message.status === 'streaming'`）默认展开并跟随滚动；`done` 后自动折叠**。实现方式：`defaultExpanded` 绑 computed，不用命令式切换，避免时序 bug。
- 每步左侧序号徽章（circle + primary 轻色），步骤间 1px 竖线
- `action` + 后续 `observation` 合并为一张 `ToolCallCard`：工具名 + 耗时 NTag + 参数块 + 结果块
- 失败步骤：图标换 ⚠️，边框用 `--color-danger`

### 6.6 ComposerBar 升级

- `NInput type=textarea`，`autosize={ minRows: 1, maxRows: 6 }`
- Enter 发送 / Shift+Enter 换行（自定义 keydown 拦截）
- 左下工具区：附件图标（disabled 占位）、快捷指令 `/`（disabled 占位）、字符计数 `{n}/2000`
- 右下发送按钮：空闲 = "发送"（primary）、sending = "停止"（danger）
- **停止按钮实现说明**：当前 `stores/workspace.ts` 与 `api/` 层**没有** abort/cancel 逻辑。本次重构需新增：在 store 内保存 `EventSource` / `AbortController` 引用，暴露 `stopStreaming()` action；SSE 被中断后把最后一条 assistant 消息标记为 `interrupted` 并在 UI 显示。若该工作量超预期，可退化为"停止按钮仅在前端结束跟随流式渲染，不真正中断后端请求"的一期行为，但必须在实施计划中明确取舍。
- 整体 sticky 底部，`--color-surface` 背景 + 1px top border + `--radius-lg`

### 6.7 ContextPanel

- 外层 `NCard` + 内部 `NForm`
- 顶部 `NTag` 显示当前会话已提取上下文条目数
- 表单：订单号 / 类型 `NSelect` / 优先级 `NSelect` / 备注 `NInput type=textarea`
- 提交 `NButton` 主色，loading 态，成功/失败经全局 `window.$message`

### 6.8 不动的部分

- `stores/workspace.ts`（状态逻辑、消息结构、SSE 处理）
- `api/`（SSE 连接、事件解析）
- 后端 SSE 事件名：`thought` / `action` / `observation` / `answer` / `done` / `error`

## 7. 知识库 & 工单（轻度跟随）

**只换皮，不改结构、不新增功能、不改逻辑。**

### 知识库

- `KnowledgeView.vue`：外层改用 `.page` 类（tokens 驱动）或 `NCard`
- `KnowledgeSidebar.vue`：分类列表改用 `NTree` 或 `NMenu`（按现有数据结构决定）
- `KnowledgeDocumentTable.vue`：改用 `NDataTable`
- 搜索筛选：`NInput` + `NSelect`
- 空态：`NEmpty`

### 工单

- `TicketFilters.vue`：`NForm inline` + `NSelect` / `NDatePicker` / `NInput`
- `TicketList.vue`：`NDataTable`，状态列用 `NTag`（info/success/warning/error 对应状态）
- `TicketDetailPanel.vue`：`NDescriptions` + `NTimeline`（若原有历史数据）；保持原显示位置

## 8. 测试策略

### 现有测试

- `src/layouts/AppShell.spec.ts`
- `src/components/workspace/ChatTimeline.spec.ts`
- `src/components/workspace/ComposerBar.spec.ts`
- `src/views/WorkspaceView.spec.ts`
- e2e: `tests/console-chat.spec.ts`

### 原则

- **行为测试必须全绿**：store 逻辑、SSE 事件处理、工单生成流程不变
- **选择器测试需改写**：`.chat-timeline__bubble` 等 class 选择器换为 `data-testid` / Testing Library 的 `getByRole` / `getByText`
- **新组件单测**：`ChatMessage.spec.ts`、`ChatProcessTrace.spec.ts`、`ToolCallCard.spec.ts`、`TopBar.spec.ts`、`stores/theme.spec.ts`
- **e2e**：console-chat 断言更新 + 新增一条"主题切换"e2e（点击按钮 → `html[data-theme]` 变化 → 关键组件颜色变化）

### 顺序
1. 先写 `stores/theme` 单测 → 实现
2. 把现有 spec 的 class 选择器改为 testid 断言（此时组件未换，保证从头到尾测试持续绿）
3. 组件替换按第 9 节顺序推进，每步跑测试

## 9. 迁移顺序

严格顺序执行，每步结束跑 `npm run test` + `npm run build`；标 e2e 步骤额外跑 `npm run test:e2e`。

0. **选择器改造**：现有 spec / e2e 的 class 选择器改为 `data-testid`
1. **装依赖 + tokens + 主题 store**：新建 `tokens.css` / `theme/index.ts` / `stores/theme.ts`，`main.ts` 引入
2. **Providers 接入**：新建 `AppProviders.vue`，包住根组件
3. **替换旧 theme.css**：拆分成 `tokens.css` + `layout.css` + 组件 scoped，逐类替换为 CSS 变量
4. **AppShell + TopBar + Sidebar(NMenu)**（e2e）
5. **ComposerBar 重写**
6. **ChatMessage + ChatProcessTrace + ToolCallCard**（3 个 commit）
7. **ChatTimeline 变薄**（e2e）
8. **SessionRail + ContextPanel**
9. **知识库 + 工单批量替换**（e2e）
10. **全局 `$message` 接入**：替换 alert/console 提示
11. **清理死代码**：删除 `theme.css` 残余类、未引用的旧样式

## 10. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| Naive UI 样式不易覆盖（尤其 NDataTable 内部） | 中 | 优先 `theme-overrides`；必要时用 `:deep()` scoped；最坏保留少量 `!important` |
| `tokens.css` 与 `theme/index.ts` 双份颜色不同步 | 中 | 文件顶部注释提示；重构末尾 grep 对照 |
| 暗→亮切换时滚动条、插画、自定义元素发糊 | 低 | 滚动条色走 token；插画用 `currentColor` 单色 |
| e2e / 单测 class 选择器批量失败 | 中 | 以第 0 步先改 testid 前置化 |
| `ChatProcessTrace` 流式展开 → done 折叠时序 bug | 中 | `defaultExpanded` 用 computed 绑 `message.status`，不用命令式切换 |
| marked + highlight.js 增加 bundle | 低 | 用 `highlight.js/lib/core` 按需注册语言，控制 gz < 80KB |
| 主色从琥珀换蓝老用户困惑 | 低 | 暗色保留琥珀；在 README / 变更日志说明 |

## 11. 范围外（明确不做）

- 全局搜索实际逻辑（仅 UI + 快捷键 + 占位弹窗）
- 通知铃铛真实数据（badge 固定 0）
- 附件上传、快捷指令 `/`
- 工单/知识库新增功能
- 后端 SSE 协议变更
- 多语言 i18n
- 动效库（framer-motion 等），仅 CSS transition

## 12. 验收标准

- `npm run build` 通过，bundle gzip 总大小较现状增量 ≤ 300KB
- `npm run test` 全绿（现有 + 新增）
- `npm run test:e2e` 全绿，含新增的主题切换测试
- 亮色 / 暗色 / auto 三种模式均可切换，刷新后保持
- 工作台在 1920px / 1280px / 980px / 720px 四个断点下均可用
- 工作台 SSE 流式行为未变：thought 实时展示、action/observation 在 ToolCallCard 展开、answer 以流式渲染、done 后 ProcessTrace 自动折叠
- 生成工单成功/失败通过 `$message` 反馈
