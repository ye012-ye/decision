<script setup lang="ts">
import { NTag } from 'naive-ui';

defineProps<{
  items: Array<{
    orderNo: string;
    title: string;
    status: string;
    priority: string;
    customerId: string;
    type: string;
  }>;
  selectedOrderNo: string;
  loading?: boolean;
}>();

const emit = defineEmits<{ select: [orderNo: string] }>();

const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
};

const statusTagType: Record<string, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  PENDING: 'default',
  PROCESSING: 'info',
  RESOLVED: 'success',
  CLOSED: 'warning',
};

const priorityLabels: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
};

const priorityTagType: Record<string, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  LOW: 'default',
  MEDIUM: 'info',
  HIGH: 'warning',
  URGENT: 'error',
};
</script>

<template>
  <section class="ticket-list">
    <div class="ticket-list__header">
      <div>
        <p class="ticket-list__eyebrow">结果列表</p>
        <h2>工单清单</h2>
      </div>
      <p class="ticket-list__meta">{{ loading ? '加载中' : `${items.length} 条` }}</p>
    </div>

    <div v-if="items.length" class="ticket-list__items">
      <button
        v-for="ticket in items"
        :key="ticket.orderNo"
        type="button"
        class="ticket-list__item"
        :data-active="ticket.orderNo === selectedOrderNo"
        @click="emit('select', ticket.orderNo)"
      >
        <div class="ticket-list__item-top">
          <strong>{{ ticket.orderNo }}</strong>
          <NTag :type="statusTagType[ticket.status] ?? 'default'" size="small" :bordered="false" round>
            {{ statusLabels[ticket.status] ?? ticket.status }}
          </NTag>
        </div>
        <p class="ticket-list__title">{{ ticket.title }}</p>
        <div class="ticket-list__item-bottom">
          <NTag :type="priorityTagType[ticket.priority] ?? 'default'" size="tiny" round>
            {{ priorityLabels[ticket.priority] ?? ticket.priority }}
          </NTag>
          <span>{{ ticket.customerId }}</span>
          <span>{{ ticket.type }}</span>
        </div>
      </button>
    </div>

    <p v-else class="ticket-list__empty">没有匹配的工单</p>
  </section>
</template>

<style scoped>
.ticket-list {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
}

.ticket-list__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.ticket-list__eyebrow,
.ticket-list__meta {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.ticket-list__header h2 {
  margin: var(--space-1) 0 0;
  font-size: 1.15rem;
  letter-spacing: -0.02em;
}

.ticket-list__items {
  display: grid;
  gap: var(--space-2);
}

.ticket-list__item {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: left;
  color: var(--color-text);
  background: var(--color-surface);
}

.ticket-list__item:hover {
  background: var(--color-surface-hover);
}

.ticket-list__item[data-active='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.ticket-list__item-top,
.ticket-list__item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.ticket-list__item-bottom span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.ticket-list__title {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
}

.ticket-list__empty {
  margin: 0;
  padding: var(--space-4) var(--space-1) var(--space-1);
  color: var(--color-text-muted);
}

@media (max-width: 980px) {
  .ticket-list__item-bottom {
    flex-wrap: wrap;
    justify-content: start;
  }
}
</style>
