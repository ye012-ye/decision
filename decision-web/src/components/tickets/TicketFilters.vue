<script setup lang="ts">
import { reactive, watch } from 'vue';
import { NButton, NInput, NSelect } from 'naive-ui';
import type { TicketQuery } from '@/api/tickets';

const props = defineProps<{
  filters: TicketQuery;
  loading?: boolean;
}>();

const emit = defineEmits<{
  refresh: [query: TicketQuery];
}>();

const query = reactive<TicketQuery>({
  orderNo: '',
  customerId: '',
  status: '',
  type: '',
  priority: '',
});

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' },
];
const typeOptions = [
  { label: '全部', value: '' },
  { label: '订单', value: 'ORDER' },
  { label: '物流', value: 'LOGISTICS' },
  { label: '账户', value: 'ACCOUNT' },
  { label: '技术故障', value: 'TECH_FAULT' },
  { label: '咨询', value: 'CONSULTATION' },
  { label: '其他', value: 'OTHER' },
];
const priorityOptions = [
  { label: '全部', value: '' },
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' },
];

watch(
  () => props.filters,
  (next) => {
    Object.assign(query, next);
  },
  { immediate: true, deep: true }
);
</script>

<template>
  <section class="ticket-filters">
    <div class="ticket-filters__header">
      <div>
        <p class="ticket-filters__eyebrow">筛选条件</p>
        <h2>工单检索</h2>
      </div>
      <NButton type="primary" :loading="loading" round @click="emit('refresh', { ...query })">
        刷新列表
      </NButton>
    </div>

    <div class="ticket-filters__grid">
      <label class="ticket-field">
        <span>工单编号</span>
        <NInput v-model:value="query.orderNo" placeholder="WO20260409001" clearable />
      </label>
      <label class="ticket-field">
        <span>客户 ID</span>
        <NInput v-model:value="query.customerId" placeholder="13800001111" clearable />
      </label>
      <div class="ticket-field">
        <span>状态</span>
        <NSelect v-model:value="query.status" :options="statusOptions" />
      </div>
      <div class="ticket-field">
        <span>类型</span>
        <NSelect v-model:value="query.type" :options="typeOptions" />
      </div>
      <div class="ticket-field">
        <span>优先级</span>
        <NSelect v-model:value="query.priority" :options="priorityOptions" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.ticket-filters {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
}

.ticket-filters__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.ticket-filters__eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.ticket-filters__header h2 {
  margin: 0;
  font-size: 1.15rem;
  letter-spacing: -0.02em;
}

.ticket-filters__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
}

.ticket-field {
  display: grid;
  gap: var(--space-2);
}

.ticket-field span {
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

@media (max-width: 1100px) {
  .ticket-filters__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .ticket-filters__header {
    flex-direction: column;
    align-items: start;
  }

  .ticket-filters__grid {
    grid-template-columns: 1fr;
  }
}
</style>
