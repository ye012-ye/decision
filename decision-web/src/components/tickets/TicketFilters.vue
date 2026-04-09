<script setup lang="ts">
import { reactive, watch } from 'vue';
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
      <button type="button" class="ticket-filters__button" :disabled="loading" @click="emit('refresh', { ...query })">
        {{ loading ? '刷新中' : '刷新列表' }}
      </button>
    </div>

    <div class="ticket-filters__grid">
      <label class="ticket-field">
        <span>工单编号</span>
        <input v-model="query.orderNo" type="text" placeholder="WO20260409001" />
      </label>
      <label class="ticket-field">
        <span>客户 ID</span>
        <input v-model="query.customerId" type="text" placeholder="13800001111" />
      </label>
      <label class="ticket-field">
        <span>状态</span>
        <select v-model="query.status">
          <option value="">全部</option>
          <option value="PENDING">待处理</option>
          <option value="PROCESSING">处理中</option>
          <option value="RESOLVED">已解决</option>
          <option value="CLOSED">已关闭</option>
        </select>
      </label>
      <label class="ticket-field">
        <span>类型</span>
        <select v-model="query.type">
          <option value="">全部</option>
          <option value="ORDER">订单</option>
          <option value="LOGISTICS">物流</option>
          <option value="ACCOUNT">账户</option>
          <option value="TECH_FAULT">技术故障</option>
          <option value="CONSULTATION">咨询</option>
          <option value="OTHER">其他</option>
        </select>
      </label>
      <label class="ticket-field">
        <span>优先级</span>
        <select v-model="query.priority">
          <option value="">全部</option>
          <option value="LOW">低</option>
          <option value="MEDIUM">中</option>
          <option value="HIGH">高</option>
          <option value="URGENT">紧急</option>
        </select>
      </label>
    </div>
  </section>
</template>

<style scoped>
.ticket-filters {
  display: grid;
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(12, 22, 34, 0.92), rgba(7, 17, 27, 0.92));
}

.ticket-filters__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.ticket-filters__eyebrow {
  margin: 0 0 4px;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.ticket-filters__header h2 {
  margin: 0;
  font-size: 1.15rem;
  letter-spacing: -0.02em;
}

.ticket-filters__button {
  padding: 10px 14px;
  border: 1px solid rgba(240, 170, 82, 0.4);
  border-radius: 999px;
  color: #10161e;
  font-weight: 700;
  background: linear-gradient(180deg, #f4ba69, #eaa547);
}

.ticket-filters__button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ticket-filters__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.ticket-field {
  display: grid;
  gap: 8px;
}

.ticket-field span {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ticket-field input,
.ticket-field select {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 12px 13px;
  color: var(--text);
  background: rgba(7, 17, 27, 0.9);
}

.ticket-field input:focus,
.ticket-field select:focus {
  border-color: rgba(240, 170, 82, 0.42);
  outline: none;
  box-shadow: 0 0 0 3px rgba(240, 170, 82, 0.14);
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
