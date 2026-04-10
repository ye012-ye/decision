<script setup lang="ts">
import type { ChatAssistantMessage, ChatMessage } from '@/types/chat';

defineProps<{
  messages: ChatMessage[];
}>();

const emit = defineEmits<{ 'toggle-process': [messageId: string] }>();

function isAssistantMessage(message: ChatMessage): message is ChatAssistantMessage {
  return message.role === 'assistant';
}
</script>

<template>
  <div class="chat-timeline">
    <p v-if="messages.length === 0" class="chat-timeline__empty">
      等待第一条消息进入作战记录。
    </p>

    <article
      v-for="message in messages"
      :key="message.id"
      class="chat-timeline__event"
      :data-type="message.role"
    >
      <p class="chat-timeline__type">
        {{ message.role === 'assistant' ? `assistant · ${message.status}` : message.role }}
      </p>
      <p class="chat-timeline__content">{{ message.content }}</p>

      <template v-if="isAssistantMessage(message) && message.process.length > 0">
        <button type="button" @click="emit('toggle-process', message.id)">
          {{ message.processExpanded ? '收起过程' : '展开过程' }}
        </button>
        <div v-if="message.processExpanded">
          <p v-for="entry in message.process" :key="entry.id">
            {{ entry.type }}: {{ entry.content }}
          </p>
        </div>
      </template>
    </article>
  </div>
</template>
