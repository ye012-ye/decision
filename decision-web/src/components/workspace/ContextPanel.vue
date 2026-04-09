<script setup lang="ts">
import { computed, reactive } from 'vue';

const props = defineProps<{
  context: {
    ticketOrderNo: string;
    activeTab: string;
  };
}>();

const emit = defineEmits<{
  create: [payload: { type: 'LOGISTICS'; title: string; description: string; customerId: string; priority: 'HIGH' }];
}>();

const draft = reactive({
  customerId: '',
  title: '',
  description: '',
});

const hasDraft = computed(() => Boolean(draft.customerId.trim() && draft.title.trim() && draft.description.trim()));

function submit() {
  if (!hasDraft.value) {
    return;
  }

  emit('create', {
    type: 'LOGISTICS',
    title: draft.title.trim(),
    description: draft.description.trim(),
    customerId: draft.customerId.trim(),
    priority: 'HIGH',
  });
}
</script>

<template>
  <aside class="context-panel">
    <div class="workspace-panel__header">
      <p class="page__eyebrow">上下文联动</p>
      <h2>工单面板</h2>
    </div>

    <div class="context-panel__status">
      <span class="context-panel__badge" :data-active="props.context.activeTab === 'ticket'">ticket</span>
      <p v-if="context.ticketOrderNo" class="context-panel__order">
        当前工单：{{ context.ticketOrderNo }}
      </p>
      <p v-else class="context-panel__hint">
        命中工单号后，这里会自动切到 ticket 视图。
      </p>
    </div>

    <div class="context-panel__draft">
      <label class="workspace-field">
        <span>客户 ID</span>
        <input v-model="draft.customerId" placeholder="CUS-10086" />
      </label>
      <label class="workspace-field">
        <span>工单标题</span>
        <input v-model="draft.title" placeholder="物流异常跟进" />
      </label>
      <label class="workspace-field">
        <span>工单描述</span>
        <textarea v-model="draft.description" rows="5" placeholder="补充上下文、诉求和处理建议" />
      </label>
      <button type="button" class="context-panel__button" :disabled="!hasDraft" @click="submit">
        手动创建工单
      </button>
    </div>
  </aside>
</template>
