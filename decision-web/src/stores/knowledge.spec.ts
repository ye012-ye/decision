import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';

type Deferred<T> = {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
};

function createDeferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;

  const promise = new Promise<T>((innerResolve, innerReject) => {
    resolve = innerResolve;
    reject = innerReject;
  });

  return { promise, resolve, reject };
}

const knowledgeMockState = vi.hoisted(() => {
  type PageResponse = { records: KnowledgeDocument[] };

  const listKnowledgeBases: KnowledgeBase[] = [
    {
      kbCode: 'base-a',
      kbName: 'A 知识库',
      description: 'A 库',
      owner: 'team-a',
      status: 1,
    },
    {
      kbCode: 'base-b',
      kbName: 'B 知识库',
      description: 'B 库',
      owner: 'team-b',
      status: 1,
    },
  ];

  const documentPages = new Map<string, Array<Deferred<PageResponse>>>();
  const documentStatuses = new Map<string, Array<Deferred<KnowledgeDocument>>>();

  const enqueue = <T>(map: Map<string, Array<Deferred<T>>>, key: string) => {
    const deferred = createDeferred<T>();
    const queue = map.get(key) ?? [];
    queue.push(deferred);
    map.set(key, queue);
    return deferred;
  };

  const dequeue = <T>(map: Map<string, Array<Deferred<T>>>, key: string) => {
    const queue = map.get(key);
    if (!queue?.length) {
      throw new Error(`Missing deferred fixture for ${key}`);
    }

    const deferred = queue.shift()!;
    if (!queue.length) {
      map.delete(key);
    }

    return deferred;
  };

  return {
    reset() {
      documentPages.clear();
      documentStatuses.clear();
    },
    listKnowledgeBases,
    enqueueDocumentPage(kbCode: string) {
      return enqueue(documentPages, kbCode);
    },
    enqueueDocumentStatus(kbCode: string, docId: string) {
      return enqueue(documentStatuses, `${kbCode}:${docId}`);
    },
    getDocumentPage(kbCode: string) {
      return dequeue(documentPages, kbCode).promise;
    },
    getDocumentStatus(kbCode: string, docId: string) {
      return dequeue(documentStatuses, `${kbCode}:${docId}`).promise;
    },
  };
});

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBases: vi.fn(async () => knowledgeMockState.listKnowledgeBases),
  getKnowledgeDocuments: vi.fn(async (kbCode: string) => knowledgeMockState.getDocumentPage(kbCode)),
  getDocumentStatus: vi.fn(async (kbCode: string, docId: string) =>
    knowledgeMockState.getDocumentStatus(kbCode, docId)
  ),
  uploadDocument: vi.fn(async () => undefined),
  createKnowledgeBase: vi.fn(async (input: KnowledgeBase) => ({ ...input, status: 1 })),
}));

import { createKnowledgeBase, uploadDocument } from '@/api/knowledge';
import { useKnowledgeStore } from './knowledge';

type MessageStub = Record<'success' | 'error' | 'warning' | 'info', ReturnType<typeof vi.fn>>;

function installMessageStub(): MessageStub {
  const stub: MessageStub = {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  };
  window.$message = stub as unknown as typeof window.$message;
  return stub;
}

describe('knowledge store', () => {
  let message: MessageStub;

  beforeEach(() => {
    setActivePinia(createPinia());
    knowledgeMockState.reset();
    vi.clearAllMocks();
    message = installMessageStub();
  });

  it('clears documents immediately when switching bases', async () => {
    const store = useKnowledgeStore();
    const firstPage = knowledgeMockState.enqueueDocumentPage('base-a');
    const secondPage = knowledgeMockState.enqueueDocumentPage('base-b');

    await store.loadBases();

    firstPage.resolve({
      records: [
        {
          docId: 'a-1',
          fileName: 'a-guide.pdf',
          status: 'PROCESSING',
        },
      ],
    });

    await store.selectBase('base-a');
    expect(store.activeKbCode).toBe('base-a');
    expect(store.documents.map((doc) => doc.fileName)).toEqual(['a-guide.pdf']);

    const switchPromise = store.selectBase('base-b');

    expect(store.activeKbCode).toBe('base-b');
    expect(store.documents).toHaveLength(0);

    secondPage.resolve({
      records: [
        {
          docId: 'b-1',
          fileName: 'b-guide.pdf',
          status: 'PROCESSING',
        },
      ],
    });

    await switchPromise;

    expect(store.documents.map((doc) => doc.fileName)).toEqual(['b-guide.pdf']);
  });

  it('ignores late document pages from an older selectBase request', async () => {
    const store = useKnowledgeStore();
    const firstPage = knowledgeMockState.enqueueDocumentPage('base-a');
    const secondPage = knowledgeMockState.enqueueDocumentPage('base-b');

    await store.loadBases();

    const firstSelect = store.selectBase('base-a');
    const secondSelect = store.selectBase('base-b');

    secondPage.resolve({
      records: [
        {
          docId: 'shared-doc',
          fileName: 'b-guide.pdf',
          status: 'PROCESSING',
        },
      ],
    });

    await secondSelect;

    firstPage.resolve({
      records: [
        {
          docId: 'shared-doc',
          fileName: 'a-guide.pdf',
          status: 'COMPLETED',
        },
      ],
    });

    await firstSelect;

    expect(store.activeKbCode).toBe('base-b');
    expect(store.documents).toHaveLength(1);
    expect(store.documents[0]?.fileName).toBe('b-guide.pdf');
  });

  it('does not write a stale refresh result into the active base after switching', async () => {
    const store = useKnowledgeStore();
    const pageA = knowledgeMockState.enqueueDocumentPage('base-a');
    const pageB = knowledgeMockState.enqueueDocumentPage('base-b');
    const refreshA = knowledgeMockState.enqueueDocumentStatus('base-a', 'doc-1');

    await store.loadBases();

    pageA.resolve({
      records: [
        {
          docId: 'doc-1',
          fileName: 'guide-a.pdf',
          status: 'PROCESSING',
        },
      ],
    });

    await store.selectBase('base-a');

    const refreshPromise = store.refreshDocumentStatus('doc-1');
    const switchPromise = store.selectBase('base-b');

    pageB.resolve({
      records: [
        {
          docId: 'doc-1',
          fileName: 'guide-b.pdf',
          status: 'READY',
        },
      ],
    });

    await switchPromise;

    refreshA.resolve({
      docId: 'doc-1',
      fileName: 'guide-a.pdf',
      status: 'COMPLETED',
    });

    await refreshPromise;

    expect(store.activeKbCode).toBe('base-b');
    expect(store.documents).toEqual([
      {
        docId: 'doc-1',
        fileName: 'guide-b.pdf',
        status: 'READY',
      },
    ]);
  });

  it('keeps the newest refresh result when document status requests resolve out of order', async () => {
    const store = useKnowledgeStore();
    const pageA = knowledgeMockState.enqueueDocumentPage('base-a');
    const slowRefresh = knowledgeMockState.enqueueDocumentStatus('base-a', 'doc-1');
    const fastRefresh = knowledgeMockState.enqueueDocumentStatus('base-a', 'doc-1');

    await store.loadBases();

    pageA.resolve({
      records: [
        {
          docId: 'doc-1',
          fileName: 'guide.pdf',
          status: 'PROCESSING',
        },
      ],
    });

    await store.selectBase('base-a');

    const slowPromise = store.refreshDocumentStatus('doc-1');
    const fastPromise = store.refreshDocumentStatus('doc-1');

    fastRefresh.resolve({
      docId: 'doc-1',
      fileName: 'guide.pdf',
      status: 'COMPLETED',
    });
    await fastPromise;

    slowRefresh.resolve({
      docId: 'doc-1',
      fileName: 'guide.pdf',
      status: 'PROCESSING',
    });
    await slowPromise;

    expect(store.documents[0]?.status).toBe('COMPLETED');
  });

  it('warns and skips upload when no base is active', async () => {
    const store = useKnowledgeStore();

    await store.uploadToActiveBase(new File(['x'], 'a.pdf'));

    expect(message.warning).toHaveBeenCalled();
    expect(vi.mocked(uploadDocument)).not.toHaveBeenCalled();
  });

  it('uploads and refreshes the document list on success', async () => {
    const store = useKnowledgeStore();
    const initialPage = knowledgeMockState.enqueueDocumentPage('base-a');

    await store.loadBases();
    const selectPromise = store.selectBase('base-a');
    initialPage.resolve({ records: [] });
    await selectPromise;

    const refreshedPage = knowledgeMockState.enqueueDocumentPage('base-a');
    const uploadPromise = store.uploadToActiveBase(new File(['x'], 'a.pdf'));
    refreshedPage.resolve({
      records: [{ docId: 'd1', fileName: 'a.pdf', status: 'PROCESSING' }],
    });
    await uploadPromise;

    expect(message.success).toHaveBeenCalled();
    expect(store.documents.map((doc) => doc.fileName)).toEqual(['a.pdf']);
  });

  it('surfaces an error message when upload fails', async () => {
    const store = useKnowledgeStore();
    const initialPage = knowledgeMockState.enqueueDocumentPage('base-a');

    await store.loadBases();
    const selectPromise = store.selectBase('base-a');
    initialPage.resolve({ records: [] });
    await selectPromise;

    vi.mocked(uploadDocument).mockRejectedValueOnce(new Error('文件类型不支持'));
    await store.uploadToActiveBase(new File(['x'], 'a.exe'));

    expect(message.error).toHaveBeenCalledWith('文件类型不支持');
  });

  it('creates a base, selects it, and reports success', async () => {
    const store = useKnowledgeStore();
    const newBasePage = knowledgeMockState.enqueueDocumentPage('base-c');

    const createPromise = store.createBase({
      kbCode: 'base-c',
      kbName: 'C 知识库',
      description: '',
      owner: 'console',
    });
    newBasePage.resolve({ records: [] });
    const created = await createPromise;

    expect(created).toBe(true);
    expect(vi.mocked(createKnowledgeBase)).toHaveBeenCalledOnce();
    expect(store.activeKbCode).toBe('base-c');
    expect(message.success).toHaveBeenCalled();
  });

  it('reports an error and returns false when base creation fails', async () => {
    const store = useKnowledgeStore();
    vi.mocked(createKnowledgeBase).mockRejectedValueOnce(new Error('知识库编码已存在'));

    const created = await store.createBase({
      kbCode: 'dup',
      kbName: 'x',
      description: '',
      owner: 'console',
    });

    expect(created).toBe(false);
    expect(message.error).toHaveBeenCalledWith('知识库编码已存在');
    expect(store.activeKbCode).toBe('');
  });
});
