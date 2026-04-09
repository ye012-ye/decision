<script setup lang="ts">
import { onMounted } from 'vue';

import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import ContextPanel from '@/components/workspace/ContextPanel.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();

onMounted(() => {
  store.bootstrap();
});
</script>

<template>
  <section class="workspace">
    <SessionRail
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="store.activateSession"
    />

    <div class="workspace__center">
      <header class="workspace__header">
        <div>
          <p class="page__eyebrow">智能客服</p>
          <h1>工作台</h1>
        </div>
        <p class="workspace__status" :data-busy="store.sending">
          {{ store.sending ? '消息发送中' : '等待新指令' }}
        </p>
      </header>

      <ChatTimeline :events="store.activeSession.events" />
      <ComposerBar :busy="store.sending" @submit="store.sendMessage" />
    </div>

    <ContextPanel :context="store.activeSession.context" @create="store.createTicketFromContext" />
  </section>
</template>
