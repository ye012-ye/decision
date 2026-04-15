<script setup lang="ts">
import { ref, watch } from 'vue';
import { NButton, NInput, NTag, NTimeline, NTimelineItem } from 'naive-ui';

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

const statusTagType: Record<string, 'default' | 'info' | 'success' | 'warning' | 'error'> = {
  PENDING: 'default',
  PROCESSING: 'info',
  RESOLVED: 'success',
  CLOSED: 'warning',
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
        <NTag :type="statusTagType[props.ticket.status] ?? 'default'" :bordered="false" round>
          {{ statusLabels[props.ticket.status] ?? props.ticket.status }}
        </NTag>
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
          <div class="ticket-detail__field">
            <span>备注</span>
            <NInput v-model:value="note" type="textarea" :rows="4" placeholder="输入处理说明" />
          </div>
          <div class="ticket-detail__actions">
            <NButton type="primary" round @click="emit('updateStatus', 'PROCESSING', note, 'console')">
              标记处理中
            </NButton>
            <NButton type="primary" round @click="emit('updateStatus', 'RESOLVED', note, 'console')">
              标记已解决
            </NButton>
          </div>
          <div class="ticket-detail__field">
            <span>关闭说明</span>
            <NInput v-model:value="resolution" type="textarea" :rows="3" placeholder="输入关闭理由" />
          </div>
          <NButton type="error" round @click="emit('close', resolution, 'console')">
            关闭工单
          </NButton>
        </section>

        <section class="ticket-detail__section">
          <p class="ticket-detail__section-title">操作日志</p>
          <NTimeline v-if="props.logs.length">
            <NTimelineItem
              v-for="log in props.logs"
              :key="`${log.action}-${log.createdAt ?? log.content}`"
              :title="log.action"
              :time="log.createdAt ?? '未知时间'"
            >
              <p class="ticket-log__content">{{ log.content }}</p>
              <template #footer>
                <span class="ticket-log__operator">{{ log.operator }}</span>
              </template>
            </NTimelineItem>
          </NTimeline>
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
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
}

.ticket-detail__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.ticket-detail__eyebrow,
.ticket-detail__section-title,
.ticket-detail__meta,
.ticket-detail__field span,
.ticket-log__operator {
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.ticket-detail__header h2 {
  margin: var(--space-1) 0 0;
  font-size: 1.25rem;
  letter-spacing: -0.03em;
}

.ticket-detail__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.ticket-detail__summary-item,
.ticket-detail__section {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-sunken);
}

.ticket-detail__summary-item {
  display: grid;
  gap: var(--space-2);
}

.ticket-detail__summary-item span {
  color: var(--color-text-muted);
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
  gap: var(--space-3);
}

.ticket-detail__section {
  display: grid;
  gap: var(--space-3);
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
  gap: var(--space-2);
}

.ticket-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ticket-log__content {
  margin: 0;
  line-height: 1.6;
}

.ticket-detail__empty {
  margin: 0;
  color: var(--color-text-muted);
}

@media (max-width: 980px) {
  .ticket-detail__summary {
    grid-template-columns: 1fr;
  }
}
</style>
