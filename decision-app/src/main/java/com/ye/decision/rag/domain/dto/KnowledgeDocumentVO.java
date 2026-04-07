package com.ye.decision.rag.domain.dto;

import com.ye.decision.rag.domain.entity.KnowledgeDocumentEntity;
import com.ye.decision.rag.domain.enums.DocumentStatus;

import java.time.LocalDateTime;

/**
 * 文档视图对象，面向前端返回，隐藏 filePath、内部 ID 等字段。
 * @author ye
 */
public record KnowledgeDocumentVO(
    String kbCode,
    String docId,
    String fileName,
    String fileType,
    Long fileSize,
    Integer chunkCount,
    DocumentStatus status,
    String errorMessage,
    String uploadedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static KnowledgeDocumentVO from(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentVO(
            entity.getKbCode(),
            entity.getDocId(),
            entity.getFileName(),
            entity.getFileType(),
            entity.getFileSize(),
            entity.getChunkCount(),
            entity.getStatus(),
            entity.getErrorMessage(),
            entity.getUploadedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
