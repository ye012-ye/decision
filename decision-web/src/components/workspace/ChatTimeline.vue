<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';

import type { ChatAssistantMessage, ChatMessage } from '@/types/chat';

const props = defineProps<{
  messages: ChatMessage[];
}>();

const emit = defineEmits<{ 'toggle-process': [messageId: string] }>();

const timelineRef = ref<HTMLElement | null>(null);
const nearBottomThreshold = 64;

function isAssistantMessage(message: ChatMessage): message is ChatAssistantMessage {
  return message.role === 'assistant';
}

function hasProcessEntries(message: ChatAssistantMessage) {
  return message.process.length > 0;
}

function shouldShowProcessRows(message: ChatAssistantMessage) {
  return message.processExpanded || message.status === 'error';
}

function isNearBottom(container: HTMLElement) {
  const distanceToBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
  return distanceToBottom <= nearBottomThreshold;
}

function scrollToBottom() {
  const container = timelineRef.value;
  if (!container) {
    return;
  }

  container.scrollTop = container.scrollHeight;
}

watch(
  () => props.messages,
  () => {
    const container = timelineRef.value;
    if (!container) {
      return;
    }

    const shouldStickToBottom = isNearBottom(container);
    nextTick(() => {
      if (shouldStickToBottom) {
        scrollToBottom();
      }
    });
  },
  { deep: true }
);
</script>

<template>
  <div ref="timelineRef" class="chat-timeline" role="log" aria-live="polite">
    <p v-if="messages.length === 0" class="chat-timeline__empty">
      等待第一条消息进入作战记录。
    </p>

    <article
      v-for="message in messages"
      :key="message.id"
      class="chat-timeline__message"
      :class="`chat-timeline__message--${message.role}`"
      :data-message-id="message.id"
    >
      <div class="chat-timeline__bubble">
        <p class="chat-timeline__meta">
          <span>{{ message.role === 'assistant' ? 'assistant' : 'user' }}</span>
          <span v-if="isAssistantMessage(message) && message.status === 'streaming'">生成中</span>
          <span v-else-if="isAssistantMessage(message) && message.status === 'error'">出错</span>
        </p>
        <p class="chat-timeline__content">{{ message.content }}</p>
      </div>

      <template v-if="isAssistantMessage(message) && hasProcessEntries(message)">
        <button
          type="button"
          class="chat-timeline__disclosure"
          @click="emit('toggle-process', message.id)"
        >
          {{ message.processExpanded ? '收起过程' : '展开过程' }}
        </button>

        <ul v-if="shouldShowProcessRows(message)" class="chat-timeline__process-list">
          <li v-for="entry in message.process" :key="entry.id" class="chat-timeline__process-row">
            <span class="chat-timeline__process-type">{{ entry.type }}</span>
            <span class="chat-timeline__process-content">{{ entry.content }}</span>
          </li>
        </ul>
      </template>
    </article>
  </div>
</template>

<style scoped>
.chat-timeline {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-height: 0;
  overflow-y: auto;
  padding: 1rem;
  border-radius: 0.75rem;
  background: #101520;
  border: 1px solid #222936;
}

.chat-timeline__empty {
  color: #8a93a3;
  text-align: center;
  margin: auto 0;
}

.chat-timeline__message {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.chat-timeline__message--user {
  align-items: flex-end;
}

.chat-timeline__message--assistant {
  align-items: flex-start;
}

.chat-timeline__bubble {
  width: min(100%, 44rem);
  padding: 0.7rem 0.85rem;
  border-radius: 0.75rem;
  border: 1px solid #2a3345;
  background: #171d2a;
}

.chat-timeline__message--user .chat-timeline__bubble {
  background: #22314f;
  border-color: #36507c;
}

.chat-timeline__meta {
  display: flex;
  gap: 0.6rem;
  margin: 0;
  font-size: 0.78rem;
  color: #97a2b8;
}

.chat-timeline__content {
  margin: 0.4rem 0 0;
  color: #e8edf8;
  line-height: 1.5;
  white-space: pre-wrap;
}

.chat-timeline__disclosure {
  border: 0;
  background: transparent;
  color: #9bb0d4;
  font-size: 0.82rem;
  padding: 0;
  cursor: pointer;
}

.chat-timeline__process-list {
  width: min(100%, 44rem);
  margin: 0;
  padding: 0.5rem 0.7rem;
  list-style: none;
  border-left: 2px solid #3b4559;
  background: #121824;
}

.chat-timeline__process-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.6rem;
  margin: 0.2rem 0;
}

.chat-timeline__process-type {
  color: #7f90ad;
  text-transform: uppercase;
  font-size: 0.72rem;
}

.chat-timeline__process-content {
  color: #b6c2d7;
  font-size: 0.83rem;
  line-height: 1.45;
}
</style>
