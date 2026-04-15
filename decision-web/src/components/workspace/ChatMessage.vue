<script setup lang="ts">
import { computed } from 'vue';
import { NAvatar, NButton, NIcon, NTooltip } from 'naive-ui';

import { CopyIcon, RefreshIcon, ThumbsDownIcon, ThumbsUpIcon, UserIcon } from '@/theme/icons';
import type { ChatMessage as ChatMessageType } from '@/types/chat';
import { renderMarkdown } from '@/utils/markdown';
import ChatProcessTrace from './ChatProcessTrace.vue';

const props = defineProps<{ message: ChatMessageType }>();

const isAssistant = computed(() => props.message.role === 'assistant');

const html = computed(() => {
  if (props.message.role !== 'assistant') return '';
  return renderMarkdown(props.message.content);
});

const streaming = computed(
  () => props.message.role === 'assistant' && props.message.status === 'streaming'
);

const errored = computed(
  () => props.message.role === 'assistant' && props.message.status === 'error'
);

async function copyContent() {
  try {
    await navigator.clipboard.writeText(props.message.content);
    window.$message?.success('已复制');
  } catch {
    window.$message?.error('复制失败');
  }
}
</script>

<template>
  <article
    class="chat-message"
    :class="`chat-message--${message.role}`"
    :data-testid="`chat-message-${message.role}`"
  >
    <NAvatar
      v-if="isAssistant"
      class="chat-message__avatar"
      round
      :size="32"
      color="var(--color-primary)"
    >
      AI
    </NAvatar>

    <div class="chat-message__body">
      <ChatProcessTrace v-if="isAssistant" :message="(message as any)" />

      <div
        class="chat-message__bubble"
        :data-testid="`chat-bubble-${message.id}`"
        :data-streaming="streaming"
        :data-errored="errored"
      >
        <template v-if="isAssistant">
          <div class="chat-message__content markdown" data-testid="chat-message-content" v-html="html" />
          <span v-if="streaming" class="chat-message__cursor" aria-hidden="true">&#x2588;</span>
        </template>
        <template v-else>
          <p class="chat-message__content chat-message__content--user" data-testid="chat-message-content">{{ message.content }}</p>
        </template>
      </div>

      <div v-if="isAssistant && !streaming" class="chat-message__actions">
        <NTooltip>
          <template #trigger>
            <NButton quaternary size="tiny" circle @click="copyContent">
              <template #icon><NIcon :component="CopyIcon" /></template>
            </NButton>
          </template>
          复制
        </NTooltip>
        <NTooltip>
          <template #trigger>
            <NButton quaternary size="tiny" circle disabled>
              <template #icon><NIcon :component="RefreshIcon" /></template>
            </NButton>
          </template>
          重新生成（即将上线）
        </NTooltip>
        <NButton quaternary size="tiny" circle disabled>
          <template #icon><NIcon :component="ThumbsUpIcon" /></template>
        </NButton>
        <NButton quaternary size="tiny" circle disabled>
          <template #icon><NIcon :component="ThumbsDownIcon" /></template>
        </NButton>
      </div>
    </div>

    <NAvatar
      v-if="!isAssistant"
      class="chat-message__avatar chat-message__avatar--user"
      round
      :size="32"
    >
      <NIcon :component="UserIcon" />
    </NAvatar>
  </article>
</template>

<style scoped>
.chat-message {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.chat-message--user {
  flex-direction: row-reverse;
}

.chat-message__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
  max-width: min(80%, 52rem);
}

.chat-message--user .chat-message__body {
  max-width: min(68%, 44rem);
  justify-items: flex-end;
}

.chat-message__bubble {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-sunken);
  overflow-wrap: anywhere;
}

.chat-message--user .chat-message__bubble {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.chat-message__bubble[data-errored='true'] {
  border-color: var(--color-danger);
}

.chat-message__content {
  margin: 0;
  line-height: 1.7;
  font-size: 15px;
  color: var(--color-text);
}

.chat-message__content--user {
  white-space: pre-wrap;
}

.chat-message__cursor {
  display: inline-block;
  width: 8px;
  animation: chat-blink 1s steps(1) infinite;
  color: var(--color-primary);
}

@keyframes chat-blink {
  50% { opacity: 0; }
}

.chat-message__actions {
  display: flex;
  gap: 4px;
  opacity: 0.78;
}
</style>

<style>
.markdown p { margin: 0 0 8px; }
.markdown p:last-child { margin-bottom: 0; }
.markdown pre {
  margin: 8px 0;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--color-bg);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 13px;
}
.markdown code { font-family: var(--font-mono); font-size: 13px; }
.markdown :not(pre) > code {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-surface-hover);
}
.markdown ul, .markdown ol { padding-left: 20px; margin: 4px 0; }
</style>
