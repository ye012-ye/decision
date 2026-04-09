<script setup lang="ts">
defineProps<{
  sessions: Array<{ id: string; title: string; events?: Array<unknown> }>;
  activeSessionId: string;
}>();

const emit = defineEmits<{ select: [sessionId: string] }>();
</script>

<template>
  <aside class="session-rail">
    <div class="workspace-panel__header">
      <p class="page__eyebrow">会话轨道</p>
      <h2>最近会话</h2>
    </div>

    <div class="session-rail__list">
      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-rail__item"
        :data-active="session.id === activeSessionId"
        type="button"
        @click="emit('select', session.id)"
      >
        <span class="session-rail__title">{{ session.title }}</span>
        <span class="session-rail__meta">{{ session.events?.length ?? 0 }} 条记录</span>
      </button>
    </div>
  </aside>
</template>
