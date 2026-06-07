<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <div class="empty-state" role="status">
    <div class="empty-state__orb" aria-hidden="true" />
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: clamp(40px, 12vh, 120px) 24px 24px;
  color: var(--color-text-muted);
  text-align: center;
}
.empty-state__orb {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--accent-gradient);
  filter: blur(1px);
  box-shadow: 0 8px 30px var(--aurora-1);
}
.empty-state__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}
.empty-state__desc {
  margin: 0;
  font-size: 14px;
}
</style>
