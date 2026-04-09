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
