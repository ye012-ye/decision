<script setup lang="ts">
import { computed } from 'vue';
import { NCollapse, NCollapseItem, NIcon, NTag } from 'naive-ui';

import { BulbIcon, ToolIcon } from '@/theme/icons';
import type { ChatAssistantMessage } from '@/types/chat';
import ToolCallCard from './ToolCallCard.vue';

const props = defineProps<{
  message: ChatAssistantMessage;
}>();

type Step =
  | { kind: 'thought'; id: string; text: string }
  | { kind: 'tool'; id: string; toolName: string; args: string; result?: string };

const steps = computed<Step[]>(() => {
  const out: Step[] = [];
  const entries = props.message.process;
  for (let i = 0; i < entries.length; i += 1) {
    const entry = entries[i];
    if (entry.type === 'thought') {
      out.push({ kind: 'thought', id: entry.id, text: entry.content });
      continue;
    }
    if (entry.type === 'action') {
      const { toolName, args } = parseAction(entry.content);
      const next = entries[i + 1];
      const result = next && next.type === 'observation' ? next.content : undefined;
      if (result !== undefined) {
        i += 1;
      }
      out.push({ kind: 'tool', id: entry.id, toolName, args, result });
      continue;
    }
    if (entry.type === 'observation') {
      // observation without preceding action — render as plain tool with empty args
      out.push({ kind: 'tool', id: entry.id, toolName: '(observation)', args: '', result: entry.content });
    }
  }
  return out;
});

function parseAction(content: string): { toolName: string; args: string } {
  // backend encodes action as "toolName | arguments"
  const pipeIdx = content.indexOf('|');
  if (pipeIdx === -1) {
    return { toolName: content.trim(), args: '' };
  }
  return {
    toolName: content.slice(0, pipeIdx).trim(),
    args: content.slice(pipeIdx + 1).trim(),
  };
}

const stepCount = computed(() => steps.value.length);

const expandedNames = computed<string[]>(() =>
  props.message.status === 'streaming' || props.message.status === 'error' ? ['trace'] : []
);
</script>

<template>
  <NCollapse
    v-if="stepCount > 0"
    class="process-trace"
    :expanded-names="expandedNames"
    arrow-placement="left"
    data-testid="chat-process-trace"
  >
    <NCollapseItem name="trace">
      <template #header>
        <div class="process-trace__header">
          <NIcon :component="BulbIcon" />
          <span>思考过程</span>
          <NTag size="tiny" :bordered="false">{{ stepCount }} 步</NTag>
          <NTag
            v-if="message.status === 'streaming'"
            size="tiny"
            type="info"
            :bordered="false"
          >进行中</NTag>
          <NTag
            v-else-if="message.status === 'error'"
            size="tiny"
            type="error"
            :bordered="false"
          >失败</NTag>
        </div>
      </template>

      <ol class="process-trace__list">
        <li v-for="(step, index) in steps" :key="step.id" class="process-trace__step">
          <span class="process-trace__number">{{ index + 1 }}</span>
          <div class="process-trace__body">
            <template v-if="step.kind === 'thought'">
              <p class="process-trace__kind">
                <NIcon :component="BulbIcon" /> 思考
              </p>
              <p class="process-trace__text">{{ step.text }}</p>
            </template>
            <template v-else>
              <p class="process-trace__kind">
                <NIcon :component="ToolIcon" /> 调用工具
              </p>
              <ToolCallCard
                :tool-name="step.toolName"
                :args="step.args"
                :result="step.result"
              />
            </template>
          </div>
        </li>
      </ol>
    </NCollapseItem>
  </NCollapse>
</template>

<style scoped>
.process-trace {
  max-width: 100%;
}

.process-trace__header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
}

.process-trace__list {
  margin: 0;
  padding: 0 0 0 var(--space-2);
  list-style: none;
  display: grid;
  gap: var(--space-3);
}

.process-trace__step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: var(--space-3);
  align-items: start;
}

.process-trace__number {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.process-trace__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.process-trace__kind {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.process-trace__text {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}
</style>
