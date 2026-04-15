<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { NTag } from 'naive-ui';

import KnowledgeDocumentTable from '@/components/knowledge/KnowledgeDocumentTable.vue';
import KnowledgeSidebar from '@/components/knowledge/KnowledgeSidebar.vue';
import { useKnowledgeStore } from '@/stores/knowledge';

const store = useKnowledgeStore();
let refreshTimer: number | undefined;
let pollingInFlight = false;

async function refreshProcessingDocuments() {
  if (pollingInFlight || !store.activeKbCode) {
    return;
  }

  pollingInFlight = true;

  const processingDocIds = store.documents
    .filter((document) => document.status === 'PROCESSING')
    .map((document) => document.docId);

  try {
    const kbCode = store.activeKbCode;
    await Promise.all(processingDocIds.map((docId) => store.refreshDocumentStatus(docId, kbCode)));
  } finally {
    pollingInFlight = false;
  }
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
      <NTag
        :type="store.loading ? 'warning' : 'success'"
        :bordered="false"
        round
        role="status"
        aria-live="polite"
      >
        {{ store.loading ? '知识库加载中' : '知识库已同步' }}
      </NTag>
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
  gap: var(--space-4);
}

.knowledge-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.knowledge-page__header h1 {
  margin: var(--space-1) 0 0;
  font-size: clamp(2rem, 2.2vw, 2.8rem);
  letter-spacing: -0.04em;
}

.knowledge-page__body {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: var(--space-4);
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
