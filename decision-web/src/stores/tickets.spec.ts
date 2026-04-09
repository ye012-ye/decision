import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { TicketQuery } from '@/api/tickets';
import type { Ticket, TicketLog } from '@/types/tickets';

const ticketMockState = vi.hoisted(() => {
  type TicketRow = Ticket;

  const createTicket = (overrides: Partial<TicketRow>): TicketRow => ({
    orderNo: '',
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'PENDING',
    title: '',
    description: '',
    customerId: '',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: null,
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:00:00',
    resolvedAt: null,
    ...overrides,
  });

  let tickets: TicketRow[] = [];
  let logs: Record<string, TicketLog[]> = {};

  const reset = () => {
    tickets = [
      createTicket({
        orderNo: 'WO20260409001',
        status: 'PENDING',
        title: '物流延迟',
        description: '三天未更新',
        customerId: '13800001111',
      }),
      createTicket({
        orderNo: 'WO20260409002',
        status: 'PROCESSING',
        title: '账户处理',
        description: '人工复核中',
        customerId: '13800002222',
      }),
    ];

    logs = {
      WO20260409001: [
        {
          action: 'CREATE',
          operator: 'agent',
          content: '创建工单 WO20260409001',
          createdAt: '2026-04-09T10:00:00',
        },
      ],
      WO20260409002: [
        {
          action: 'CREATE',
          operator: 'agent',
          content: '创建工单 WO20260409002',
          createdAt: '2026-04-09T10:00:00',
        },
      ],
    };
  };

  const cloneTicket = (ticket: TicketRow) => ({ ...ticket });
  const cloneLogs = (orderNo: string) => (logs[orderNo] ?? []).map((entry) => ({ ...entry }));

  const matches = (ticket: TicketRow, query: TicketQuery) => {
    if (query.orderNo && !ticket.orderNo.includes(query.orderNo)) {
      return false;
    }
    if (query.customerId && !ticket.customerId.includes(query.customerId)) {
      return false;
    }
    if (query.status && ticket.status !== query.status) {
      return false;
    }
    if (query.type && ticket.type !== query.type) {
      return false;
    }
    if (query.priority && ticket.priority !== query.priority) {
      return false;
    }
    return true;
  };

  const findTicket = (orderNo: string) => tickets.find((ticket) => ticket.orderNo === orderNo);

  const appendLog = (orderNo: string, entry: TicketLog) => {
    logs[orderNo] = [...(logs[orderNo] ?? []), entry];
  };

  reset();

  return {
    reset,
    listTickets(query: TicketQuery = {}) {
      return tickets.filter((ticket) => matches(ticket, query)).map(cloneTicket);
    },
    getTicket(orderNo: string) {
      const ticket = findTicket(orderNo);
      if (!ticket) {
        throw new Error(`Missing ticket fixture: ${orderNo}`);
      }
      return cloneTicket(ticket);
    },
    getTicketLogs(orderNo: string) {
      return cloneLogs(orderNo);
    },
    updateTicketStatus(orderNo: string, status: Ticket['status'], note: string, operator: string) {
      const ticket = findTicket(orderNo);
      if (!ticket) {
        throw new Error(`Missing ticket fixture: ${orderNo}`);
      }

      ticket.status = status;
      ticket.updatedAt = '2026-04-09T10:05:00';
      appendLog(orderNo, {
        action: 'STATUS_UPDATE',
        operator,
        content: note || `状态变更为 ${status}`,
        createdAt: '2026-04-09T10:05:00',
      });

      return cloneTicket(ticket);
    },
    closeTicket(orderNo: string, resolution: string, operator: string) {
      const ticket = findTicket(orderNo);
      if (!ticket) {
        throw new Error(`Missing ticket fixture: ${orderNo}`);
      }

      ticket.status = 'CLOSED';
      ticket.resolution = resolution;
      ticket.updatedAt = '2026-04-09T10:10:00';
      ticket.resolvedAt = '2026-04-09T10:10:00';
      appendLog(orderNo, {
        action: 'CLOSE',
        operator,
        content: resolution || '关闭工单',
        createdAt: '2026-04-09T10:10:00',
      });

      return cloneTicket(ticket);
    },
  };
});

vi.mock('@/api/tickets', () => ({
  listTickets: vi.fn(async (query: TicketQuery = {}) => ticketMockState.listTickets(query)),
  getTicket: vi.fn(async (orderNo: string) => ticketMockState.getTicket(orderNo)),
  getTicketLogs: vi.fn(async (orderNo: string) => ticketMockState.getTicketLogs(orderNo)),
  updateTicketStatus: vi.fn(async (orderNo: string, status: Ticket['status'], note: string, operator: string) =>
    ticketMockState.updateTicketStatus(orderNo, status, note, operator)
  ),
  closeTicket: vi.fn(async (orderNo: string, resolution: string, operator: string) =>
    ticketMockState.closeTicket(orderNo, resolution, operator)
  ),
}));

import { useTicketsStore } from './tickets';

function createFilters(overrides: Partial<TicketQuery> = {}): TicketQuery {
  return {
    orderNo: '',
    customerId: '',
    status: '',
    type: '',
    priority: '',
    ...overrides,
  };
}

describe('tickets store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    ticketMockState.reset();
  });

  it('loads ticket list and auto-selects the first ticket on initial load', async () => {
    const store = useTicketsStore();

    await store.loadTickets();

    expect(store.items.map((ticket) => ticket.orderNo)).toEqual(['WO20260409001', 'WO20260409002']);
    expect(store.selected?.orderNo).toBe('WO20260409001');
    expect(store.logs).toHaveLength(1);
    expect(store.logs[0]?.content).toContain('WO20260409001');
  });

  it('applies filters by switching selection or clearing when the result set is empty', async () => {
    const store = useTicketsStore();

    await store.loadTickets();
    await store.applyFilters(createFilters({ status: 'PROCESSING' }));

    expect(store.items).toHaveLength(1);
    expect(store.items[0]?.orderNo).toBe('WO20260409002');
    expect(store.selected?.orderNo).toBe('WO20260409002');
    expect(store.logs[0]?.content).toContain('WO20260409002');

    await store.applyFilters(createFilters({ orderNo: 'NOPE' }));

    expect(store.items).toHaveLength(0);
    expect(store.selected).toBeNull();
    expect(store.logs).toHaveLength(0);
  });

  it('syncs the list and selection after updating a ticket status', async () => {
    const store = useTicketsStore();

    await store.loadTickets();
    await store.updateSelectedStatus('PROCESSING', '开始处理', 'agent');

    expect(store.items.find((ticket) => ticket.orderNo === 'WO20260409001')?.status).toBe('PROCESSING');
    expect(store.selected?.orderNo).toBe('WO20260409001');
    expect(store.selected?.status).toBe('PROCESSING');
    expect(store.logs).toHaveLength(2);

    await store.applyFilters(createFilters({ status: 'PROCESSING' }));
    await store.updateSelectedStatus('RESOLVED', '已解决', 'agent');

    expect(store.items).toHaveLength(1);
    expect(store.items[0]?.orderNo).toBe('WO20260409002');
    expect(store.selected?.orderNo).toBe('WO20260409002');
    expect(store.selected?.status).toBe('PROCESSING');
  });

  it('syncs the list and selection after closing the selected ticket', async () => {
    const store = useTicketsStore();

    await store.loadTickets();
    await store.applyFilters(createFilters({ status: 'PROCESSING' }));
    await store.closeSelected('已补发', 'agent');

    expect(store.items).toHaveLength(0);
    expect(store.selected).toBeNull();
    expect(store.logs).toHaveLength(0);
  });
});
