<script setup lang="ts">
import { computed, reactive } from 'vue';
import { NButton, NCard, NForm, NFormItem, NInput, NTag } from 'naive-ui';

const props = defineProps<{
  context: { ticketOrderNo: string; activeTab: string };
}>();

const emit = defineEmits<{
  (e: 'create', payload: {
    type: 'LOGISTICS';
    title: string;
    description: string;
    customerId: string;
    priority: 'HIGH';
  }): void;
}>();

const draft = reactive({
  customerId: '',
  title: '',
  description: '',
});

const loading = computed(() => false); // reserved for future busy state
const canSubmit = computed(() =>
  Boolean(draft.customerId.trim() && draft.title.trim() && draft.description.trim())
);

async function submit() {
  if (!canSubmit.value) return;
  try {
    emit('create', {
      type: 'LOGISTICS',
      title: draft.title.trim(),
      description: draft.description.trim(),
      customerId: draft.customerId.trim(),
      priority: 'HIGH',
    });
    window.$message?.success('已发起工单创建');
  } catch (error) {
    window.$message?.error(error instanceof Error ? error.message : '创建失败');
  }
}
</script>

<template>
  <NCard
    class="context-panel"
    data-testid="context-panel"
    :bordered="true"
    content-style="padding: 20px 22px;"
  >
    <template #header>
      <div class="context-panel__header">
        <span>上下文</span>
        <NTag size="small" :type="context.activeTab === 'ticket' ? 'success' : 'default'" :bordered="false">
          {{ context.activeTab }}
        </NTag>
      </div>
    </template>

    <p v-if="context.ticketOrderNo" class="context-panel__order">
      当前工单：<strong>{{ context.ticketOrderNo }}</strong>
    </p>
    <p v-else class="context-panel__hint">命中工单号后，这里会自动切换到 ticket 视图。</p>

    <NForm label-placement="top" :show-require-mark="false">
      <NFormItem label="客户 ID">
        <NInput v-model:value="draft.customerId" placeholder="CUS-10086" />
      </NFormItem>
      <NFormItem label="工单标题">
        <NInput v-model:value="draft.title" placeholder="物流异常跟进" />
      </NFormItem>
      <NFormItem label="工单描述">
        <NInput
          v-model:value="draft.description"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 8 }"
          placeholder="补充上下文、诉求和处理建议"
        />
      </NFormItem>

      <NButton
        type="primary"
        block
        :disabled="!canSubmit"
        :loading="loading"
        data-testid="context-create"
        @click="submit"
      >
        手动创建工单
      </NButton>
    </NForm>
  </NCard>
</template>

<style scoped>
.context-panel {
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
}
.context-panel__header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.context-panel__order {
  margin: 0 0 var(--space-3);
  color: var(--color-text);
}
.context-panel__hint {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
