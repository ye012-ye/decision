<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { NDrawer, NDrawerContent } from 'naive-ui';
import { useRoute } from 'vue-router';

import SidebarNav from '@/components/common/SidebarNav.vue';
import TopBar from './TopBar.vue';

const route = useRoute();

const drawerOpen = ref(false);
const isMobile = ref(false);

function evaluateViewport() {
  isMobile.value = window.matchMedia('(max-width: 980px)').matches;
}

onMounted(() => {
  evaluateViewport();
  window.addEventListener('resize', evaluateViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', evaluateViewport);
});

const sidebarVisible = computed(() => !isMobile.value);

// Close the drawer when navigating to a new route
watch(() => route.path, () => {
  drawerOpen.value = false;
});

function openSearch() {
  window.$dialog?.info({
    title: '全局搜索',
    content: '全局搜索将在后续版本提供。',
    positiveText: '好的',
  });
}
</script>

<template>
  <div class="app-shell">
    <TopBar @toggle-sidebar="drawerOpen = true" @open-search="openSearch" />
    <div class="app-shell__body">
      <SidebarNav v-if="sidebarVisible" />
      <NDrawer v-else v-model:show="drawerOpen" :width="280" placement="left">
        <NDrawerContent title="导航" closable>
          <SidebarNav />
        </NDrawerContent>
      </NDrawer>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
