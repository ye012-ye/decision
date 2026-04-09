<script setup lang="ts">
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

const priorityLabels: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
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
          <span>{{ statusLabels[ticket.status] ?? ticket.status }}</span>
        </div>
        <p class="ticket-list__title">{{ ticket.title }}</p>
        <div class="ticket-list__item-bottom">
          <span>{{ priorityLabels[ticket.priority] ?? ticket.priority }}</span>
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
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(12, 22, 34, 0.88), rgba(7, 17, 27, 0.9));
}

.ticket-list__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.ticket-list__eyebrow,
.ticket-list__meta {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.ticket-list__header h2 {
  margin: 4px 0 0;
  font-size: 1.15rem;
  letter-spacing: -0.02em;
}

.ticket-list__items {
  display: grid;
  gap: 10px;
}

.ticket-list__item {
  display: grid;
  gap: 8px;
  padding: 14px 15px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 18px;
  text-align: left;
  color: var(--text);
  background: rgba(255, 255, 255, 0.02);
}

.ticket-list__item[data-active='true'] {
  border-color: rgba(240, 170, 82, 0.34);
  background: rgba(240, 170, 82, 0.08);
}

.ticket-list__item-top,
.ticket-list__item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ticket-list__item-top span,
.ticket-list__item-bottom span {
  color: var(--muted);
  font-size: 12px;
}

.ticket-list__title {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
}

.ticket-list__empty {
  margin: 0;
  padding: 18px 4px 4px;
  color: var(--muted);
}

@media (max-width: 980px) {
  .ticket-list__item-bottom {
    flex-wrap: wrap;
    justify-content: start;
  }
}
</style>
