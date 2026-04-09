<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';

import KnowledgeDocumentTable from '@/components/knowledge/KnowledgeDocumentTable.vue';
import KnowledgeSidebar from '@/components/knowledge/KnowledgeSidebar.vue';
import { useKnowledgeStore } from '@/stores/knowledge';

const store = useKnowledgeStore();
let refreshTimer: number | undefined;

async function refreshProcessingDocuments() {
  const processingDocIds = store.documents
    .filter((document) => document.status === 'PROCESSING')
    .map((document) => document.docId);

  await Promise.all(processingDocIds.map((docId) => store.refreshDocumentStatus(docId)));
}

onMounted(async () => {
  await store.loadBases();

  if (store.activeKbCode) {
    await store.selectBase(store.activeKbCode);
  }

  refreshTimer = window.setInterval(() => {
    void refreshProcessingDocuments();
  }, 5000);
});

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer);
  }
});
</script>

<template>
  <section class="page knowledge-page">
    <header class="page__header knowledge-page__header">
      <div>
        <p class="page__eyebrow">RAG</p>
        <h1>知识库管理</h1>
      </div>
      <p class="knowledge-page__status" :data-loading="store.loading">
        {{ store.loading ? '知识库加载中' : '知识库已同步' }}
      </p>
    </header>

    <div class="knowledge-page__body">
      <KnowledgeSidebar
        :bases="store.bases"
        :active-kb-code="store.activeKbCode"
        @select="store.selectBase"
      />
      <KnowledgeDocumentTable
        :kb-code="store.activeKbCode"
        :documents="store.documents"
        @upload="store.uploadToActiveBase"
        @refresh="store.refreshDocumentStatus"
      />
    </div>
  </section>
</template>

<style scoped>
.knowledge-page {
  display: grid;
  gap: 18px;
}

.knowledge-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.knowledge-page__header h1 {
  margin: 4px 0 0;
  font-size: clamp(2rem, 2.2vw, 2.8rem);
  letter-spacing: -0.04em;
}

.knowledge-page__status {
  margin: 0;
  padding: 8px 12px;
  border: 1px solid rgba(64, 194, 173, 0.24);
  border-radius: 999px;
  color: var(--muted);
  background: rgba(64, 194, 173, 0.08);
}

.knowledge-page__status[data-loading='true'] {
  border-color: rgba(240, 170, 82, 0.3);
  color: #f7d19f;
  background: rgba(240, 170, 82, 0.12);
}

.knowledge-page__body {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 18px;
}

@media (max-width: 1120px) {
  .knowledge-page__body {
    grid-template-columns: 1fr;
  }

  .knowledge-page__header {
    flex-direction: column;
    align-items: start;
  }
}
</style>
