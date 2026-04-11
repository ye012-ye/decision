<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';

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
  return message.processExpanded;
}

function processListId(messageId: string) {
  return `chat-process-${messageId}`;
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

const autoScrollSignal = computed(() =>
  props.messages
    .map((message) => {
      if (message.role === 'assistant') {
        return `${message.id}:${message.content}:${message.process.length}`;
      }

      return `${message.id}:${message.content}`;
    })
    .join('|')
);

watch(
  autoScrollSignal,
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
  }
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
      :data-testid="`chat-message-${message.role}`"
    >
      <div class="chat-timeline__bubble" :data-testid="`chat-bubble-${message.id}`">
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
          :aria-expanded="message.processExpanded"
          :aria-controls="processListId(message.id)"
          @click="emit('toggle-process', message.id)"
        >
          {{ message.processExpanded ? '收起过程' : '展开过程' }}
        </button>

        <ul
          v-if="shouldShowProcessRows(message)"
          :id="processListId(message.id)"
          class="chat-timeline__process-list"
        >
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
  gap: 0.75rem;
  min-height: 0;
  overflow-y: auto;
}

.chat-timeline__message {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.chat-timeline__message--user {
  align-items: flex-end;
}

.chat-timeline__message--assistant {
  align-items: flex-start;
}

.chat-timeline__bubble,
.chat-timeline__process-list {
  max-width: min(100%, 44rem);
}

.chat-timeline__content {
  white-space: pre-wrap;
}

.chat-timeline__process-list {
  margin: 0;
  padding-left: 1.125rem;
}

.chat-timeline__process-row {
  display: grid;
  grid-template-columns: auto 1fr;
  column-gap: 0.5rem;
}
</style>
