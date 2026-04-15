<script setup lang="ts">
import { onMounted } from 'vue';
import { NTag } from 'naive-ui';

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
      <NTag
        :type="store.loading ? 'warning' : 'success'"
        :bordered="false"
        round
        role="status"
        aria-live="polite"
      >
        {{ store.loading ? '列表加载中' : '列表已同步' }}
      </NTag>
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
  gap: var(--space-4);
}

.tickets-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.tickets-page__header h1 {
  margin: var(--space-1) 0 0;
  font-size: clamp(2rem, 2.4vw, 2.8rem);
  letter-spacing: -0.04em;
}

.tickets-page__body {
  display: grid;
  grid-template-columns: minmax(340px, 420px) minmax(0, 1fr);
  gap: var(--space-4);
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
