<script setup lang="ts">
import { NIcon } from 'naive-ui';

import { KnowledgeIcon } from '@/theme/icons';

defineProps<{
  bases: Array<{
    kbCode: string;
    kbName: string;
    description?: string;
    owner?: string;
    status?: number;
  }>;
  activeKbCode: string;
}>();

const emit = defineEmits<{ select: [kbCode: string] }>();

function statusLabel(status?: number) {
  if (status === 1) {
    return '在线';
  }

  if (status === 0) {
    return '停用';
  }

  return '未知';
}
</script>

<template>
  <aside class="knowledge-sidebar" data-testid="knowledge-sidebar">
    <div class="workspace-panel__header">
      <p class="knowledge-sidebar__eyebrow">知识库选择</p>
      <h2>库列表</h2>
    </div>

    <div v-if="bases.length" class="knowledge-sidebar__list">
      <button
        v-for="base in bases"
        :key="base.kbCode"
        type="button"
        class="knowledge-sidebar__item"
        :data-active="base.kbCode === activeKbCode"
        @click="emit('select', base.kbCode)"
      >
        <strong><NIcon :component="KnowledgeIcon" :size="16" /> {{ base.kbName }}</strong>
        <span>{{ base.description || '暂无简介' }}</span>
        <small>{{ base.owner || 'unknown' }} · {{ statusLabel(base.status) }}</small>
      </button>
    </div>

    <p v-else class="knowledge-sidebar__empty">先加载知识库，再选择一个工作集。</p>
  </aside>
</template>

<style scoped>
.knowledge-sidebar {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.knowledge-sidebar__eyebrow {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.knowledge-sidebar h2 {
  margin: var(--space-1) 0 0;
  font-size: 1.1rem;
  letter-spacing: -0.02em;
}

.knowledge-sidebar__list {
  display: grid;
  gap: var(--space-2);
}

.knowledge-sidebar__item {
  display: grid;
  gap: 5px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: left;
  color: var(--color-text);
  background: var(--color-surface-hover);
}

.knowledge-sidebar__item[data-active='true'] {
  border-color: var(--color-primary-soft-strong);
  background: var(--color-primary-soft);
}

.knowledge-sidebar__item strong {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 15px;
}

.knowledge-sidebar__item span,
.knowledge-sidebar__item small,
.knowledge-sidebar__empty {
  color: var(--color-text-muted);
}

.knowledge-sidebar__empty {
  margin: 0;
  line-height: 1.6;
}
</style>
