import type { Ticket, TicketLog, TicketPriority, TicketStatus, TicketType } from '@/types/tickets';
import { requestJson } from './http';

export interface TicketQuery {
  orderNo?: string;
  customerId?: string;
  status?: TicketStatus | '';
  type?: TicketType | '';
  priority?: TicketPriority | '';
}

export interface TicketCreatePayload {
  type: TicketType;
  priority?: TicketPriority;
  title: string;
  description: string;
  customerId: string;
  sessionId?: string;
}

export async function listTickets(query: TicketQuery = {}) {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });
  return requestJson<Ticket[]>(`/api/work-orders?${params.toString()}`);
}

export function createTicket(payload: TicketCreatePayload) {
  return requestJson<Ticket>('/api/work-orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function getTicket(orderNo: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}`);
}

export function getTicketLogs(orderNo: string) {
  return requestJson<TicketLog[]>(`/api/work-orders/${orderNo}/logs`);
}

export function updateTicketStatus(orderNo: string, status: TicketStatus, note: string, operator: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, note, operator }),
  });
}

export function closeTicket(orderNo: string, resolution: string, operator: string) {
  return requestJson<Ticket>(`/api/work-orders/${orderNo}/close`, {
    method: 'POST',
    body: JSON.stringify({ resolution, operator }),
  });
}
