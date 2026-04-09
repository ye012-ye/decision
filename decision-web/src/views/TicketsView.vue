<script setup lang="ts">
import { onMounted } from 'vue';

import TicketDetailPanel from '@/components/tickets/TicketDetailPanel.vue';
import TicketFilters from '@/components/tickets/TicketFilters.vue';
import TicketList from '@/components/tickets/TicketList.vue';
import { useTicketsStore } from '@/stores/tickets';

const store = useTicketsStore();

onMounted(() => {
  void store.loadTickets();
});
</script>

<template>
  <section class="page tickets-page">
    <header class="page__header tickets-page__header">
      <div>
        <p class="page__eyebrow">Service</p>
        <h1>工单管理</h1>
      </div>
      <p class="tickets-page__status" :data-loading="store.loading">
        {{ store.loading ? '列表加载中' : '等待筛选' }}
      </p>
    </header>

    <TicketFilters :filters="store.filters" :loading="store.loading" @refresh="store.applyFilters" />

    <div class="tickets-page__body">
      <TicketList
        :items="store.items"
        :selected-order-no="store.selected?.orderNo ?? ''"
        :loading="store.loading"
        @select="store.selectTicket"
      />
      <TicketDetailPanel
        :ticket="store.selected"
        :logs="store.logs"
        @update-status="store.updateSelectedStatus"
        @close="store.closeSelected"
      />
    </div>
  </section>
</template>

<style scoped>
.tickets-page {
  display: grid;
  gap: 18px;
}

.tickets-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.tickets-page__header h1 {
  margin: 4px 0 0;
  font-size: clamp(2rem, 2.4vw, 2.8rem);
  letter-spacing: -0.04em;
}

.tickets-page__status {
  margin: 0;
  padding: 8px 12px;
  border: 1px solid rgba(64, 194, 173, 0.22);
  border-radius: 999px;
  color: var(--muted);
  background: rgba(64, 194, 173, 0.08);
}

.tickets-page__status[data-loading='true'] {
  border-color: rgba(240, 170, 82, 0.3);
  color: #f7d19f;
  background: rgba(240, 170, 82, 0.12);
}

.tickets-page__body {
  display: grid;
  grid-template-columns: minmax(340px, 420px) minmax(0, 1fr);
  gap: 18px;
}

@media (max-width: 1120px) {
  .tickets-page__body {
    grid-template-columns: 1fr;
  }

  .tickets-page__header {
    flex-direction: column;
    align-items: start;
  }
}
</style>
