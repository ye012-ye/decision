export interface KnowledgeBase {
  kbCode: string;
  kbName: string;
  description: string;
  owner: string;
  status: number;
}

export interface KnowledgeBaseCreateInput {
  kbCode: string;
  kbName: string;
  description: string;
  owner: string;
}

export interface KnowledgeDocument {
  docId: string;
  fileName: string;
  status: string;
}
