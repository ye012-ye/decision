export type TicketStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED';
export type TicketType = 'ORDER' | 'LOGISTICS' | 'ACCOUNT' | 'TECH_FAULT' | 'CONSULTATION' | 'OTHER';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Ticket {
  orderNo: string;
  type: TicketType;
  priority: TicketPriority;
  status: TicketStatus;
  title: string;
  description: string;
  customerId: string;
  assignee: string | null;
  assigneeGroup: string | null;
  resolution: string | null;
  sessionId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  resolvedAt: string | null;
}

export interface TicketLog {
  action: string;
  operator: string;
  content: string;
  createdAt: string | null;
}
