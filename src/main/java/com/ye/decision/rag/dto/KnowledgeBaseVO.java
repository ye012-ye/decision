package com.ye.decision.rag.dto;

import com.ye.decision.rag.entity.KnowledgeBaseEntity;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(
    String kbCode,
    String kbName,
    String description,
    String owner,
    Integer status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static KnowledgeBaseVO from(KnowledgeBaseEntity entity) {
        return new KnowledgeBaseVO(
            entity.getKbCode(),
            entity.getKbName(),
            entity.getDescription(),
            entity.getOwner(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
