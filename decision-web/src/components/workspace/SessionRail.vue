<script setup lang="ts">
import { computed, h, ref } from 'vue';
import { NButton, NDropdown, NEmpty, NIcon, NInput, NScrollbar, NTooltip } from 'naive-ui';

import { AddIcon, AutoIcon, MoonIcon, MoreIcon, SearchIcon, SunIcon } from '@/theme/icons';
import { useThemeStore } from '@/stores/theme';
import type { ChatMessage } from '@/types/chat';

const props = defineProps<{
  sessions: Array<{ id: string; title: string; messages?: ChatMessage[]; messageCount?: number }>;
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
