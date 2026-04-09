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

        if (!this.items.length) {
          this.selected = null;
          this.logs = [];
          return;
        }

        const selectedOrderNo = this.selected?.orderNo;
        const nextOrderNo =
          selectedOrderNo && this.items.some((ticket) => ticket.orderNo === selectedOrderNo)
            ? selectedOrderNo
            : this.items[0].orderNo;

        await this.selectTicket(nextOrderNo);
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

      await updateTicketStatus(this.selected.orderNo, status, note, operator);
      await this.loadTickets();
    },
    async closeSelected(resolution: string, operator: string) {
      if (!this.selected) {
        return;
      }

      await closeTicket(this.selected.orderNo, resolution, operator);
      await this.loadTickets();
    },
  },
});
