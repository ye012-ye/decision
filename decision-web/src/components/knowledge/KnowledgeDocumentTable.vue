<script setup lang="ts">
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
        <input type="file" @change="onFileChange" />
      </label>
    </header>

    <div v-if="documents.length" class="knowledge-documents__list">
      <article v-for="document in documents" :key="document.docId" class="knowledge-documents__row">
        <div class="knowledge-documents__meta">
          <strong>{{ document.fileName }}</strong>
          <span :data-status="document.status">{{ document.status }}</span>
        </div>
        <button
          type="button"
          class="knowledge-documents__button"
          @click="emit('refresh', document.docId)"
        >
          刷新状态
        </button>
      </article>
    </div>

    <p v-else class="knowledge-documents__empty">这个知识库还没有文档，先上传一个文件。</p>
  </section>
</template>

<style scoped>
.knowledge-documents {
  display: grid;
  gap: 18px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(12, 22, 34, 0.9), rgba(7, 17, 27, 0.95));
  box-shadow: var(--shadow);
}

.knowledge-documents__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.knowledge-documents__eyebrow {
  margin: 0 0 4px;
  color: var(--muted);
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
  gap: 8px;
  justify-items: end;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.knowledge-documents__upload input {
  width: min(320px, 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 11px 12px;
  color: var(--text);
  background: rgba(7, 17, 27, 0.9);
}

.knowledge-documents__list {
  display: grid;
  gap: 12px;
}

.knowledge-documents__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 15px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.02);
}

.knowledge-documents__meta {
  display: grid;
  gap: 6px;
}

.knowledge-documents__meta strong {
  font-size: 15px;
}

.knowledge-documents__meta span {
  width: fit-content;
  padding: 5px 9px;
  border-radius: 999px;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.04);
}

.knowledge-documents__meta span[data-status='PROCESSING'] {
  color: #f7d19f;
  background: rgba(240, 170, 82, 0.12);
}

.knowledge-documents__meta span[data-status='COMPLETED'] {
  color: #b4f0e6;
  background: rgba(64, 194, 173, 0.1);
}

.knowledge-documents__button {
  min-width: 96px;
  padding: 10px 14px;
  border: 1px solid rgba(240, 170, 82, 0.32);
  border-radius: 999px;
  color: #10161e;
  font-weight: 700;
  background: linear-gradient(180deg, #f4ba69, #eaa547);
}

.knowledge-documents__button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.knowledge-documents__empty {
  margin: 0;
  color: var(--muted);
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

  .knowledge-documents__upload input {
    width: 100%;
  }
}
</style>
