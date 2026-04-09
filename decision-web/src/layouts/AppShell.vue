<script setup lang="ts">
import SidebarNav from '@/components/common/SidebarNav.vue';
</script>

<template>
  <div class="app-shell">
    <SidebarNav />
    <main class="app-shell__main">
      <RouterView />
    </main>
  </div>
</template>
