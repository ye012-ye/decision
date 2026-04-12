<script setup lang="ts">
import { computed } from 'vue';
import { NCode, NTag } from 'naive-ui';

const props = defineProps<{
  toolName: string;
  args: string;
  result?: string;
  durationMs?: number;
  failed?: boolean;
}>();

const argsPretty = computed(() => tryFormatJson(props.args));
const resultPretty = computed(() => (props.result ? tryFormatJson(props.result) : ''));

function tryFormatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
</script>

<template>
  <div class="tool-call" :data-failed="failed">
    <header class="tool-call__header">
      <span class="tool-call__name">{{ toolName }}</span>
      <NTag v-if="durationMs !== undefined" size="small" :bordered="false">{{ durationMs }}ms</NTag>
      <NTag v-if="failed" size="small" type="error" :bordered="false">失败</NTag>
    </header>

    <section class="tool-call__block">
      <p class="tool-call__label">参数</p>
      <NCode :code="argsPretty" language="json" word-wrap />
    </section>

    <section v-if="result" class="tool-call__block">
      <p class="tool-call__label">结果</p>
      <NCode :code="resultPretty" language="json" word-wrap />
    </section>
  </div>
</template>

<style scoped>
.tool-call {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
}

.tool-call[data-failed='true'] {
  border-color: var(--color-danger);
}

.tool-call__header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.tool-call__name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.tool-call__label {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.tool-call__block {
  display: grid;
  gap: 4px;
}
</style>
