<script setup lang="ts">
import { computed, ref } from 'vue';
import { NButton, NIcon, NInput } from 'naive-ui';

import { SendIcon, StopIcon } from '@/theme/icons';

const props = defineProps<{ busy: boolean }>();
const emit = defineEmits<{
  (e: 'submit', message: string): void;
  (e: 'stop'): void;
}>();

const MAX_LEN = 2000;
const value = ref('');
const helperId = 'composer-helper-text';

const trimmed = computed(() => value.value.trim());
const canSend = computed(() => trimmed.value.length > 0 && !props.busy);
const overLimit = computed(() => value.value.length > MAX_LEN);

function submit() {
  if (!canSend.value || overLimit.value) return;
  emit('submit', trimmed.value);
  value.value = '';
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    submit();
  }
}
</script>

<template>
  <form class="composer" @submit.prevent="submit" data-testid="composer">
    <NInput
      v-model:value="value"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      placeholder="输入客户诉求或问题... (Enter 发送 · Shift+Enter 换行)"
      :aria-describedby="helperId"
      :maxlength="MAX_LEN + 200"
      data-testid="composer-input"
      @keydown="onKeydown"
    />

    <div class="composer__footer">
      <p :id="helperId" class="composer__helper" role="status" aria-live="polite">
        <span v-if="busy">正在整理回复…</span>
        <span v-else>Enter 发送 · Shift + Enter 换行</span>
        <span class="composer__count" :data-over="overLimit">{{ value.length }}/{{ MAX_LEN }}</span>
      </p>

      <NButton
        v-if="!busy"
        type="primary"
        :disabled="!canSend || overLimit"
        data-testid="composer-submit"
        @click="submit"
      >
        <template #icon><NIcon :component="SendIcon" /></template>
        发送
      </NButton>
      <NButton
        v-else
        type="error"
        data-testid="composer-stop"
        @click="emit('stop')"
      >
        <template #icon><NIcon :component="StopIcon" /></template>
        停止
      </NButton>
    </div>
  </form>
</template>

<style scoped>
.composer {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}
.composer__helper {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
}
.composer__count[data-over='true'] {
  color: var(--color-danger);
  font-weight: 600;
}
</style>
