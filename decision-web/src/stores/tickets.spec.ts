import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/tickets', () => ({
  listTickets: vi.fn(async () => [
    {
      orderNo: 'WO20260409001',
      type: 'LOGISTICS',
      priority: 'HIGH',
      status: 'PENDING',
      title: '物流延迟',
      description: '三天未更新',
      customerId: '13800001111',
      assignee: '物流专员',
      assigneeGroup: '物流组',
      resolution: null,
      sessionId: 'session-1',
      createdAt: '2026-04-09T10:00:00',
      updatedAt: '2026-04-09T10:00:00',
      resolvedAt: null,
    },
  ]),
  getTicket: vi.fn(async (orderNo: string) => ({
    orderNo,
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'PENDING',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: null,
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:00:00',
    resolvedAt: null,
  })),
  getTicketLogs: vi.fn(async () => [
    { action: 'CREATE', operator: 'agent', content: '创建工单', createdAt: '2026-04-09T10:00:00' },
  ]),
  updateTicketStatus: vi.fn(async () => ({
    orderNo: 'WO20260409001',
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'PROCESSING',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: null,
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:05:00',
    resolvedAt: null,
  })),
  closeTicket: vi.fn(async () => ({
    orderNo: 'WO20260409001',
    type: 'LOGISTICS',
    priority: 'HIGH',
    status: 'CLOSED',
    title: '物流延迟',
    description: '三天未更新',
    customerId: '13800001111',
    assignee: '物流专员',
    assigneeGroup: '物流组',
    resolution: '已补发',
    sessionId: 'session-1',
    createdAt: '2026-04-09T10:00:00',
    updatedAt: '2026-04-09T10:10:00',
    resolvedAt: '2026-04-09T10:10:00',
  })),
}));

import { useTicketsStore } from './tickets';

describe('tickets store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads ticket list and selected ticket details', async () => {
    const store = useTicketsStore();
    await store.loadTickets();
    await store.selectTicket('WO20260409001');

    expect(store.items).toHaveLength(1);
    expect(store.selected?.orderNo).toBe('WO20260409001');
    expect(store.logs).toHaveLength(1);
  });

  it('updates and closes the selected ticket', async () => {
    const store = useTicketsStore();
    await store.loadTickets();
    await store.selectTicket('WO20260409001');
    await store.updateSelectedStatus('PROCESSING', '开始处理', 'agent');
    expect(store.selected?.status).toBe('PROCESSING');

    await store.closeSelected('已补发', 'agent');
    expect(store.selected?.status).toBe('CLOSED');
  });
});
