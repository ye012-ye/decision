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
