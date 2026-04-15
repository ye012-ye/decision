<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <div class="empty-state" role="status">
    <svg class="empty-state__art" viewBox="0 0 120 80" aria-hidden="true">
      <rect x="10" y="14" width="100" height="52" rx="10" fill="none" stroke="currentColor" stroke-width="2" />
      <circle cx="34" cy="40" r="4" fill="currentColor" />
      <circle cx="60" cy="40" r="4" fill="currentColor" />
      <circle cx="86" cy="40" r="4" fill="currentColor" />
    </svg>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-text-muted);
}
.empty-state__art {
  width: 120px;
  color: var(--color-text-subtle);
}
.empty-state__title {
  margin: 0;
  font-size: 15px;
  color: var(--color-text);
}
.empty-state__desc {
  margin: 0;
  font-size: 13px;
}
</style>
