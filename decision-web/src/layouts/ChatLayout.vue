<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { NButton, NDrawer, NDrawerContent, NDropdown, NIcon, NTag } from 'naive-ui';
import type { DropdownOption } from 'naive-ui';

import BackgroundAurora from './BackgroundAurora.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import { MenuIcon } from '@/theme/icons';
import { useAuthStore } from '@/stores/auth';
import { useWorkspaceStore } from '@/stores/workspace';

const auth = useAuthStore();
const store = useWorkspaceStore();

const drawerOpen = ref(false);
const isMobile = ref(false);

function evaluateViewport() {
  isMobile.value = window.matchMedia('(max-width: 980px)').matches;
}

onMounted(() => {
  store.bootstrap();
  evaluateViewport();
  window.addEventListener('resize', evaluateViewport);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', evaluateViewport);
});

const railVisible = computed(() => !isMobile.value);

function onSelect(id: string) {
  store.activateSession(id);
  drawerOpen.value = false;
}
function onCreate() {
  store.newConversation();
  drawerOpen.value = false;
}
function onDelete(id: string) {
  store.removeSession(id);
}

const userOptions: DropdownOption[] = [
  { key: 'logout', label: '退出登录' },
];

function onUserSelect(key: string) {
  if (key === 'logout') {
    auth.logout();
    window.location.assign('/login');
  }
}
</script>

<template>
  <div class="chat-layout">
    <BackgroundAurora />

    <SessionRail
      v-if="railVisible"
      class="chat-layout__rail"
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="onSelect"
      @create="onCreate"
      @delete="onDelete"
    />
    <NDrawer v-else v-model:show="drawerOpen" :width="288" placement="left">
      <NDrawerContent title="会话" closable>
        <SessionRail
          :sessions="store.sessions"
          :active-session-id="store.activeSessionId"
          @select="onSelect"
          @create="onCreate"
          @delete="onDelete"
        />
      </NDrawerContent>
    </NDrawer>

    <section class="chat-layout__pane">
      <header class="chat-pane__header glass">
        <NButton
          v-if="isMobile"
          class="chat-pane__menu"
          quaternary
          circle
          aria-label="打开会话"
          @click="drawerOpen = true"
        >
          <template #icon><NIcon :component="MenuIcon" /></template>
        </NButton>
        <h1 class="chat-pane__title">{{ store.activeSession.title }}</h1>
        <NTag :type="store.sending ? 'warning' : 'success'" :bordered="false" round size="small">
          {{ store.sending ? '正在生成' : '在线' }}
        </NTag>

        <div style="flex:1" />
        <NDropdown :options="userOptions" trigger="click" @select="onUserSelect">
          <NButton quaternary size="small">
            {{ auth.user?.nickname ?? '用户' }}
          </NButton>
        </NDropdown>
      </header>

      <ChatTimeline :messages="store.activeSession.messages" @suggest="store.sendMessage" />

      <ComposerBar :busy="store.sending" @submit="store.sendMessage" @stop="store.stopStreaming" />
    </section>
  </div>
</template>

<style scoped>
.chat-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr);
  gap: var(--space-4);
  height: 100vh;
  padding: var(--space-4);
}
.chat-layout__pane {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  min-width: 0;
  min-height: 0;
}
.chat-pane__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl);
}
.chat-pane__title {
  flex: 1 1 auto;
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 980px) {
  .chat-layout {
    grid-template-columns: 1fr;
    padding: var(--space-2);
  }
}
</style>
