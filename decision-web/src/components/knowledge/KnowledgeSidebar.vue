<script setup lang="ts">
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
  <aside class="knowledge-sidebar">
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
        <strong>{{ base.kbName }}</strong>
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
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(11, 20, 31, 0.92), rgba(7, 17, 27, 0.88));
  box-shadow: var(--shadow);
  backdrop-filter: blur(18px);
}

.knowledge-sidebar__eyebrow {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.knowledge-sidebar h2 {
  margin: 4px 0 0;
  font-size: 1.1rem;
  letter-spacing: -0.02em;
}

.knowledge-sidebar__list {
  display: grid;
  gap: 10px;
}

.knowledge-sidebar__item {
  display: grid;
  gap: 5px;
  padding: 14px 15px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 18px;
  text-align: left;
  color: var(--text);
  background: rgba(255, 255, 255, 0.02);
}

.knowledge-sidebar__item[data-active='true'] {
  border-color: rgba(240, 170, 82, 0.32);
  background: rgba(240, 170, 82, 0.08);
}

.knowledge-sidebar__item strong {
  font-size: 15px;
}

.knowledge-sidebar__item span,
.knowledge-sidebar__item small,
.knowledge-sidebar__empty {
  color: var(--muted);
}

.knowledge-sidebar__empty {
  margin: 0;
  line-height: 1.6;
}
</style>
