<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
  ticket: null | {
    orderNo: string;
    title: string;
    description: string;
    status: string;
    type: string;
    priority: string;
    customerId: string;
    assignee: string | null;
    assigneeGroup: string | null;
    resolution: string | null;
    createdAt: string | null;
    updatedAt: string | null;
    resolvedAt: string | null;
  };
  logs: Array<{ action: string; operator: string; content: string; createdAt: string | null }>;
}>();

const emit = defineEmits<{
  updateStatus: [status: 'PROCESSING' | 'RESOLVED', note: string, operator: string];
  close: [resolution: string, operator: string];
}>();

const note = ref('');
const resolution = ref('');

const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
};

watch(
  () => props.ticket?.orderNo,
  () => {
    note.value = '';
    resolution.value = '';
  },
  { immediate: true }
);
</script>

<template>
  <aside class="ticket-detail">
    <template v-if="props.ticket">
      <div class="ticket-detail__header">
        <div>
          <p class="ticket-detail__eyebrow">当前工单</p>
          <h2>{{ props.ticket.orderNo }}</h2>
        </div>
        <p class="ticket-detail__status">{{ statusLabels[props.ticket.status] ?? props.ticket.status }}</p>
      </div>

      <div class="ticket-detail__summary">
        <div class="ticket-detail__summary-item">
          <span>标题</span>
          <strong>{{ props.ticket.title }}</strong>
        </div>
        <div class="ticket-detail__summary-item">
          <span>客户 ID</span>
          <strong>{{ props.ticket.customerId }}</strong>
        </div>
        <div class="ticket-detail__summary-item">
          <span>处理组</span>
          <strong>{{ props.ticket.assigneeGroup ?? '未分配' }}</strong>
        </div>
        <div class="ticket-detail__summary-item">
          <span>处理人</span>
          <strong>{{ props.ticket.assignee ?? '未分配' }}</strong>
        </div>
      </div>

      <div class="ticket-detail__body">
        <section class="ticket-detail__section">
          <p class="ticket-detail__section-title">详情</p>
          <p class="ticket-detail__description">{{ props.ticket.description }}</p>
          <p class="ticket-detail__meta">类型：{{ props.ticket.type }} | 优先级：{{ props.ticket.priority }}</p>
          <p class="ticket-detail__meta">更新：{{ props.ticket.updatedAt ?? '未知' }}</p>
        </section>

        <section class="ticket-detail__section">
          <p class="ticket-detail__section-title">处理动作</p>
          <label class="ticket-detail__field">
            <span>备注</span>
            <textarea v-model="note" rows="4" placeholder="输入处理说明"></textarea>
          </label>
          <div class="ticket-detail__actions">
            <button type="button" @click="emit('updateStatus', 'PROCESSING', note, 'console')">标记处理中</button>
            <button type="button" @click="emit('updateStatus', 'RESOLVED', note, 'console')">标记已解决</button>
          </div>
          <label class="ticket-detail__field">
            <span>关闭说明</span>
            <textarea v-model="resolution" rows="3" placeholder="输入关闭理由"></textarea>
          </label>
          <button type="button" class="ticket-detail__close" @click="emit('close', resolution, 'console')">关闭工单</button>
        </section>

        <section class="ticket-detail__section">
          <p class="ticket-detail__section-title">操作日志</p>
          <div v-if="props.logs.length" class="ticket-detail__logs">
            <article v-for="log in props.logs" :key="`${log.action}-${log.createdAt ?? log.content}`" class="ticket-log">
              <div class="ticket-log__top">
                <strong>{{ log.action }}</strong>
                <span>{{ log.operator }}</span>
              </div>
              <p>{{ log.content }}</p>
              <time>{{ log.createdAt ?? '未知时间' }}</time>
            </article>
          </div>
          <p v-else class="ticket-detail__empty">暂无日志</p>
        </section>
      </div>
    </template>

    <p v-else class="ticket-detail__empty">选择一条工单查看详情</p>
  </aside>
</template>

<style scoped>
.ticket-detail {
  display: grid;
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(12, 22, 34, 0.94), rgba(7, 17, 27, 0.94));
}

.ticket-detail__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.ticket-detail__eyebrow,
.ticket-detail__section-title,
.ticket-detail__meta,
.ticket-detail__status,
.ticket-detail__field span,
.ticket-log time {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.ticket-detail__header h2 {
  margin: 4px 0 0;
  font-size: 1.25rem;
  letter-spacing: -0.03em;
}

.ticket-detail__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ticket-detail__summary-item,
.ticket-detail__section {
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.02);
}

.ticket-detail__summary-item {
  display: grid;
  gap: 8px;
}

.ticket-detail__summary-item span {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ticket-detail__summary-item strong {
  font-size: 15px;
  font-weight: 600;
}

.ticket-detail__body {
  display: grid;
  gap: 12px;
}

.ticket-detail__section {
  display: grid;
  gap: 12px;
}

.ticket-detail__section-title {
  margin: 0;
}

.ticket-detail__description {
  margin: 0;
  line-height: 1.7;
}

.ticket-detail__field {
  display: grid;
  gap: 8px;
}

.ticket-detail__field textarea {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 12px 13px;
  color: var(--text);
  background: rgba(7, 17, 27, 0.9);
  resize: vertical;
}

.ticket-detail__field textarea:focus {
  border-color: rgba(240, 170, 82, 0.42);
  outline: none;
  box-shadow: 0 0 0 3px rgba(240, 170, 82, 0.14);
}

.ticket-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ticket-detail__actions button,
.ticket-detail__close {
  padding: 10px 14px;
  border: 1px solid rgba(240, 170, 82, 0.32);
  border-radius: 999px;
  color: #10161e;
  font-weight: 700;
  background: linear-gradient(180deg, #f4ba69, #eaa547);
}

.ticket-detail__close {
  width: fit-content;
}

.ticket-detail__logs {
  display: grid;
  gap: 10px;
}

.ticket-log {
  display: grid;
  gap: 8px;
  padding: 12px 13px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
}

.ticket-log__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ticket-log__top span {
  color: var(--muted);
  font-size: 12px;
}

.ticket-log p {
  margin: 0;
  line-height: 1.6;
}

.ticket-log time {
  font-style: normal;
}

.ticket-detail__empty {
  margin: 0;
  color: var(--muted);
}

@media (max-width: 980px) {
  .ticket-detail__summary {
    grid-template-columns: 1fr;
  }
}
</style>
