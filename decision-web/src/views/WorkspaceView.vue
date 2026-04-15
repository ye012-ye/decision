<script setup lang="ts">
import { onMounted } from 'vue';
import { NTag } from 'naive-ui';

import ChatTimeline from '@/components/workspace/ChatTimeline.vue';
import ComposerBar from '@/components/workspace/ComposerBar.vue';
import ContextPanel from '@/components/workspace/ContextPanel.vue';
import SessionRail from '@/components/workspace/SessionRail.vue';
import { useWorkspaceStore } from '@/stores/workspace';

const store = useWorkspaceStore();

onMounted(() => {
  store.bootstrap();
});

function onCreateSession() {
  window.$message?.info('新建会话（占位）');
}
</script>

<template>
  <section class="workspace">
    <SessionRail
      :sessions="store.sessions"
      :active-session-id="store.activeSessionId"
      @select="store.activateSession"
      @create="onCreateSession"
    />

    <div class="workspace__center">
      <header class="workspace__header">
        <div>
          <p class="page__eyebrow">智能客服</p>
          <h1>工作台</h1>
        </div>
        <NTag
          :type="store.sending ? 'warning' : 'success'"
          :bordered="false"
          round
        >
          {{ store.sending ? '正在生成回复' : '等待新指令' }}
        </NTag>
      </header>

      <ChatTimeline
        :messages="store.activeSession.messages"
        @suggest="store.sendMessage"
      />

      <ComposerBar
        :busy="store.sending"
        @submit="store.sendMessage"
        @stop="store.stopStreaming"
      />
    </div>

    <ContextPanel
      :context="store.activeSession.context"
      @create="store.createTicketFromContext"
    />
  </section>
</template>
