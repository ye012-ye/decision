import { defineStore } from 'pinia';

import { getDocumentStatus, getKnowledgeDocuments, listKnowledgeBases, uploadDocument } from '@/api/knowledge';
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';

export const useKnowledgeStore = defineStore('knowledge', {
  state: () => ({
    bases: [] as KnowledgeBase[],
    activeKbCode: '',
    documents: [] as KnowledgeDocument[],
    loading: false,
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
      this.activeKbCode = kbCode;
      const page = await getKnowledgeDocuments(kbCode);
      this.documents = page.records;
    },
    async uploadToActiveBase(file: File) {
      if (!this.activeKbCode) {
        return;
      }

      await uploadDocument(this.activeKbCode, file);
      await this.selectBase(this.activeKbCode);
    },
    async refreshDocumentStatus(docId: string) {
      if (!this.activeKbCode) {
        return;
      }

      const latest = await getDocumentStatus(this.activeKbCode, docId);
      this.documents = this.documents.map((doc) => (doc.docId === docId ? latest : doc));
    },
  },
});
