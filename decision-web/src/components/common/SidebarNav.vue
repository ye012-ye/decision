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
    <NMenu :options="options" :value="activeKey" :indent="16" :root-indent="16" />
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
