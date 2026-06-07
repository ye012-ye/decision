<script setup lang="ts">
import { computed, ref } from 'vue';
import { NIcon, NInput } from 'naive-ui';

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
  <form class="composer glass" @submit.prevent="submit" data-testid="composer">
    <NInput
      v-model:value="value"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      placeholder="给智能助手发消息…  (Enter 发送 · Shift+Enter 换行)"
      :aria-describedby="helperId"
      :maxlength="MAX_LEN + 200"
      data-testid="composer-input"
      class="composer__input"
      @keydown="onKeydown"
    />

    <div class="composer__footer">
      <p :id="helperId" class="composer__helper" role="status" aria-live="polite">
        <span v-if="busy">正在整理回复…</span>
        <span v-else>Enter 发送 · Shift + Enter 换行</span>
        <span class="composer__count" :data-over="overLimit">{{ value.length }}/{{ MAX_LEN }}</span>
      </p>

      <button
        v-if="!busy"
        type="button"
        class="composer__send"
        :disabled="!canSend || overLimit"
        data-testid="composer-submit"
        @click="submit"
      >
        <NIcon :component="SendIcon" /><span>发送</span>
      </button>
      <button
        v-else
        type="button"
        class="composer__stop"
        data-testid="composer-stop"
        @click="emit('stop')"
      >
        <NIcon :component="StopIcon" /><span>停止</span>
      </button>
    </div>
  </form>
</template>

<style scoped>
.composer {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-3) var(--space-2);
  border-radius: var(--radius-xl);
}
.composer__input :deep(.n-input),
.composer__input :deep(.n-input .n-input__textarea-el) {
  background: transparent;
}
.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-1);
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
.composer__send,
.composer__stop {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: 0;
  border-radius: 999px;
  font-weight: 600;
  color: #fff;
  transition: transform 0.15s ease, opacity 0.2s ease, box-shadow 0.2s ease;
}
.composer__send {
  background: var(--accent-gradient);
  box-shadow: 0 6px 18px var(--aurora-1);
}
.composer__send:hover:not(:disabled) {
  transform: translateY(-1px);
}
.composer__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.composer__stop {
  background: var(--color-danger);
}
.composer__stop:hover {
  transform: translateY(-1px);
}
</style>
