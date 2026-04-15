<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { NButton, NIcon, NTag } from 'naive-ui';

import { ArrowDownIcon } from '@/theme/icons';
import type { ChatMessage as ChatMessageType } from '@/types/chat';
import ChatMessageComp from './ChatMessage.vue';
import EmptyState from '@/components/common/EmptyState.vue';

const props = defineProps<{ messages: ChatMessageType[] }>();
const emit = defineEmits<{ 'suggest': [text: string] }>();

const timelineRef = ref<HTMLElement | null>(null);
const stickToBottom = ref(true);
const showJumpButton = ref(false);
const nearBottomThreshold = 64;
const jumpThreshold = 200;

const suggestions = ['订单 A2025 的物流状态？', '帮我总结这位客户的诉求', '生成一个退款工单草稿'];

function isNearBottom(el: HTMLElement) {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= nearBottomThreshold;
}

function handleScroll() {
  const el = timelineRef.value;
  if (!el) return;
  stickToBottom.value = isNearBottom(el);
  showJumpButton.value = el.scrollHeight - el.scrollTop - el.clientHeight > jumpThreshold;
}

function scrollToBottom(smooth = false) {
  const el = timelineRef.value;
  if (!el) return;
  el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
}

const autoScrollSignal = computed(() =>
  props.messages
    .map((m) => (m.role === 'assistant' ? `${m.id}:${m.content}:${m.process.length}` : `${m.id}:${m.content}`))
    .join('|')
);

watch(autoScrollSignal, () => {
  if (!stickToBottom.value) return;
  nextTick(() => scrollToBottom(false));
});

onMounted(() => {
  timelineRef.value?.addEventListener('scroll', handleScroll);
  scrollToBottom(false);
});

onBeforeUnmount(() => {
  timelineRef.value?.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="chat-timeline-wrapper">
    <div ref="timelineRef" class="chat-timeline" role="log" aria-live="polite" data-testid="chat-timeline">
      <EmptyState
        v-if="messages.length === 0"
        title="开始一段对话"
        description="发送一条问题，或试试下面的示例："
      >
        <div class="chat-timeline__suggestions">
          <NTag
            v-for="suggestion in suggestions"
            :key="suggestion"
            checkable
            round
            @update:checked="emit('suggest', suggestion)"
          >
            {{ suggestion }}
          </NTag>
        </div>
      </EmptyState>

      <ChatMessageComp
        v-for="message in messages"
        :key="message.id"
        :message="message"
      />
    </div>

    <NButton
      v-if="showJumpButton"
      class="chat-timeline__jump"
      circle
      type="primary"
      data-testid="chat-jump-bottom"
      @click="scrollToBottom(true)"
    >
      <template #icon><NIcon :component="ArrowDownIcon" /></template>
    </NButton>
  </div>
</template>

<style scoped>
.chat-timeline-wrapper {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}

.chat-timeline {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-height: 0;
  padding: var(--space-4);
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.chat-timeline__suggestions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 8px;
}

.chat-timeline__jump {
  position: absolute;
  right: 20px;
  bottom: 20px;
  box-shadow: var(--shadow-md);
}
</style>
