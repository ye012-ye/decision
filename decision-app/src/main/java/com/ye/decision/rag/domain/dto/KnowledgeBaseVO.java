package com.ye.decision.rag.domain.dto;

import com.ye.decision.rag.domain.entity.KnowledgeBaseEntity;
import com.ye.decision.rag.domain.enums.KbStatus;

import java.time.LocalDateTime;

/**
 * 知识库视图对象，面向前端返回，隐藏内部 ID 等敏感字段。
 * @author ye
 */
public record KnowledgeBaseVO(
    String kbCode,
    String kbName,
    String description,
    String owner,
    KbStatus status,
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
