import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBases: vi.fn(async () => [
    {
      kbCode: 'product-docs',
      kbName: '产品文档库',
      description: '产品说明',
      owner: 'tech-team',
      status: 1,
    },
  ]),
  getKnowledgeDocuments: vi.fn(async () => ({
    records: [
      {
        docId: 'doc-1',
        fileName: 'guide.pdf',
        status: 'PROCESSING',
      },
    ],
  })),
  getDocumentStatus: vi.fn(async () => ({
    docId: 'doc-1',
    fileName: 'guide.pdf',
    status: 'COMPLETED',
  })),
}));

import { useKnowledgeStore } from './knowledge';

describe('knowledge store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads knowledge bases and documents for the active base', async () => {
    const store = useKnowledgeStore();
    await store.loadBases();
    await store.selectBase('product-docs');

    expect(store.bases[0].kbCode).toBe('product-docs');
    expect(store.documents[0].status).toBe('PROCESSING');
  });

  it('refreshes a document status after polling', async () => {
    const store = useKnowledgeStore();
    await store.loadBases();
    await store.selectBase('product-docs');
    await store.refreshDocumentStatus('doc-1');

    expect(store.documents[0].status).toBe('COMPLETED');
  });
});
