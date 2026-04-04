package com.ye.decision.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ye.decision.rag.domain.enums.KbStatus;

import java.time.LocalDateTime;

/**
 * 知识库实体，对应 {@code knowledge_base} 表。
 * <p>
 * 每个知识库通过 {@code kbCode} 唯一标识，关联 Milvus 中的向量数据
 * 和 {@link KnowledgeDocumentEntity} 中的文档元数据。
 *
 * @author ye
 */
@TableName("knowledge_base")
public class KnowledgeBaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("kb_code")
    private String kbCode;

    @TableField("kb_name")
    private String kbName;

    private String description;

    private String owner;

    /** 1=active 0=disabled */
    private KbStatus status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public KnowledgeBaseEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKbCode() { return kbCode; }
    public void setKbCode(String kbCode) { this.kbCode = kbCode; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public KbStatus getStatus() { return status; }
    public void setStatus(KbStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
