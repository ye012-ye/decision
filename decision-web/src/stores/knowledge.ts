import { defineStore } from 'pinia';

import { getDocumentStatus, getKnowledgeDocuments, listKnowledgeBases, uploadDocument } from '@/api/knowledge';
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';

export const useKnowledgeStore = defineStore('knowledge', {
  state: () => ({
    bases: [] as KnowledgeBase[],
    activeKbCode: '',
    documents: [] as KnowledgeDocument[],
    loading: false,
    selectRequestId: 0,
    baseVersions: {} as Record<string, number>,
    refreshRequestIds: {} as Record<string, number>,
  }),
  actions: {
    async loadBases() {
      this.loading = true;

      try {
        this.bases = await listKnowledgeBases();

        if (!this.activeKbCode && this.bases[0]) {
          this.activeKbCode = this.bases[0].kbCode;
        }
      } finally {
        this.loading = false;
      }
    },
    async selectBase(kbCode: string) {
      this.selectRequestId += 1;
      const requestId = this.selectRequestId;
      const baseVersion = (this.baseVersions[kbCode] ?? 0) + 1;

      this.activeKbCode = kbCode;
      this.documents = [];
      this.baseVersions[kbCode] = baseVersion;

      const page = await getKnowledgeDocuments(kbCode);
      if (this.selectRequestId !== requestId) {
        return;
      }

      if (this.activeKbCode !== kbCode) {
        return;
      }

      if ((this.baseVersions[kbCode] ?? 0) !== baseVersion) {
        return;
      }

      this.documents = page.records;
    },
    async uploadToActiveBase(file: File) {
      if (!this.activeKbCode) {
        return;
      }

      await uploadDocument(this.activeKbCode, file);
      await this.selectBase(this.activeKbCode);
    },
    async refreshDocumentStatus(docId: string, kbCode?: string) {
      const targetKbCode = kbCode ?? this.activeKbCode;
      if (!targetKbCode) {
        return;
      }

      const requestKey = `${targetKbCode}:${docId}`;
      const requestId = (this.refreshRequestIds[requestKey] ?? 0) + 1;
      const baseVersion = this.baseVersions[targetKbCode] ?? 0;

      this.refreshRequestIds[requestKey] = requestId;

      const latest = await getDocumentStatus(targetKbCode, docId);
      if (this.activeKbCode !== targetKbCode) {
        return;
      }

      if ((this.baseVersions[targetKbCode] ?? 0) !== baseVersion) {
        return;
      }

      if (this.refreshRequestIds[requestKey] !== requestId) {
        return;
      }

      this.documents = this.documents.map((doc) => (doc.docId === docId ? latest : doc));
    },
  },
});
