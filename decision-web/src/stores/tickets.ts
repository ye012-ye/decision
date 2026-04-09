import { defineStore } from 'pinia';

import {
  closeTicket,
  getTicket,
  getTicketLogs,
  listTickets,
  updateTicketStatus,
  type TicketQuery,
} from '@/api/tickets';
import type { Ticket, TicketLog } from '@/types/tickets';

const initialFilters: TicketQuery = {
  orderNo: '',
  customerId: '',
  status: '',
  type: '',
  priority: '',
};

export const useTicketsStore = defineStore('tickets', {
  state: () => ({
    filters: { ...initialFilters },
    items: [] as Ticket[],
    selected: null as Ticket | null,
    logs: [] as TicketLog[],
    loading: false,
  }),
  actions: {
    async applyFilters(query: TicketQuery) {
      this.filters = { ...initialFilters, ...query };
      await this.loadTickets();
    },
    async loadTickets() {
      this.loading = true;

      try {
        this.items = await listTickets(this.filters);

        if (!this.selected && this.items[0]) {
          await this.selectTicket(this.items[0].orderNo);
        }
      } finally {
        this.loading = false;
      }
    },
    async selectTicket(orderNo: string) {
      const [ticket, logs] = await Promise.all([getTicket(orderNo), getTicketLogs(orderNo)]);
      this.selected = ticket;
      this.logs = logs;
    },
    async updateSelectedStatus(status: Ticket['status'], note: string, operator: string) {
      if (!this.selected) {
        return;
      }

      this.selected = await updateTicketStatus(this.selected.orderNo, status, note, operator);
      this.logs = await getTicketLogs(this.selected.orderNo);
    },
    async closeSelected(resolution: string, operator: string) {
      if (!this.selected) {
        return;
      }

      this.selected = await closeTicket(this.selected.orderNo, resolution, operator);
      this.logs = await getTicketLogs(this.selected.orderNo);
    },
  },
});
