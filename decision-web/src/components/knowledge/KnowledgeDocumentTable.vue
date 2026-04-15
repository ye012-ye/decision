<script setup lang="ts">
import { NButton, NTag } from 'naive-ui';

const emit = defineEmits<{
  upload: [file: File];
  refresh: [docId: string];
}>();

defineProps<{
  kbCode: string;
  documents: Array<{ docId: string; fileName: string; status: string }>;
}>();

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (file) {
    emit('upload', file);
    input.value = '';
  }
}
</script>

<template>
  <section class="knowledge-documents">
    <header class="knowledge-documents__header">
      <div>
        <p class="knowledge-documents__eyebrow">文档面板</p>
        <h2>{{ kbCode || '未选择知识库' }}</h2>
      </div>
      <label class="knowledge-documents__upload">
        <span>上传文档</span>
        <input type="file" class="knowledge-documents__file-input" @change="onFileChange" />
      </label>
    </header>

    <div v-if="documents.length" class="knowledge-documents__list">
      <article v-for="document in documents" :key="document.docId" class="knowledge-documents__row">
        <div class="knowledge-documents__meta">
          <strong>{{ document.fileName }}</strong>
          <NTag
            :type="document.status === 'COMPLETED' ? 'success' : document.status === 'PROCESSING' ? 'warning' : 'default'"
            :bordered="false"
            round
            size="small"
          >
            {{ document.status }}
          </NTag>
        </div>
        <NButton
          type="primary"
          size="small"
          round
          @click="emit('refresh', document.docId)"
        >
          刷新状态
        </NButton>
      </article>
    </div>

    <p v-else class="knowledge-documents__empty">这个知识库还没有文档，先上传一个文件。</p>
  </section>
</template>

<style scoped>
.knowledge-documents {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.knowledge-documents__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-4);
}

.knowledge-documents__eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.knowledge-documents__header h2 {
  margin: 0;
  font-size: 1.1rem;
  letter-spacing: -0.02em;
}

.knowledge-documents__upload {
  display: grid;
  gap: var(--space-2);
  justify-items: end;
  color: var(--color-text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.knowledge-documents__file-input {
  width: min(320px, 100%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-3);
  color: var(--color-text);
  background: var(--color-surface-sunken);
}

.knowledge-documents__list {
  display: grid;
  gap: var(--space-3);
}

.knowledge-documents__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-hover);
}

.knowledge-documents__meta {
  display: grid;
  gap: var(--space-2);
}

.knowledge-documents__meta strong {
  font-size: 15px;
}

.knowledge-documents__empty {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.6;
}

@media (max-width: 720px) {
  .knowledge-documents__header,
  .knowledge-documents__row {
    flex-direction: column;
    align-items: start;
  }

  .knowledge-documents__upload {
    justify-items: start;
    width: 100%;
  }

  .knowledge-documents__file-input {
    width: 100%;
  }
}
</style>
