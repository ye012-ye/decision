-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_code     VARCHAR(64)  NOT NULL UNIQUE COMMENT '知识库唯一编码，用于 Milvus 过滤',
    kb_name     VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    owner       VARCHAR(100) NOT NULL COMMENT '所属用户或部门',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库';

-- 知识文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_code         VARCHAR(64)  NOT NULL COMMENT '所属知识库编码',
    doc_id          VARCHAR(64)  NOT NULL UNIQUE COMMENT '文档 UUID，Milvus 中的 doc_id 元数据',
    file_name       VARCHAR(300) NOT NULL,
    file_type       VARCHAR(50)  NOT NULL COMMENT 'pdf, docx, md, html, txt',
    file_size       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_path       VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    chunk_count     INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    error_message   VARCHAR(1000) DEFAULT NULL,
    uploaded_by     VARCHAR(100) DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_code (kb_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据';
