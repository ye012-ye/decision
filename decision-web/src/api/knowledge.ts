import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge';
import { readJsonEnvelope, requestJson } from './http';

export function listKnowledgeBases() {
  return requestJson<KnowledgeBase[]>('/api/kb');
}

export function getKnowledgeDocuments(kbCode: string) {
  return requestJson<{ records: KnowledgeDocument[] }>(`/api/kb/${kbCode}/documents`);
}

export function getDocumentStatus(kbCode: string, docId: string) {
  return requestJson<KnowledgeDocument>(`/api/kb/${kbCode}/documents/${docId}/status`);
}

export async function uploadDocument(kbCode: string, file: File, uploadedBy = 'console') {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('uploadedBy', uploadedBy);

  const response = await fetch(`/api/kb/${kbCode}/documents`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    const payload = await readJsonEnvelope(response);
    if (payload) {
      throw new Error(payload.msg);
    }

    throw new Error(`上传失败: ${response.status}`);
  }

  const payload = await readJsonEnvelope<unknown>(response);
  if (payload && payload.code !== 200) {
    throw new Error(payload.msg);
  }

  if (payload) {
    return payload.data;
  }

  throw new Error(`上传失败: ${response.status}`);
}
