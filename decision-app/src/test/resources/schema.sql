CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    seq             INT          NOT NULL,
    message_type    VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_base
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_code     VARCHAR(64)  NOT NULL UNIQUE,
    kb_name     VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    owner       VARCHAR(64)  NOT NULL,
    status      INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_document
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_code       VARCHAR(64)  NOT NULL,
    doc_id        VARCHAR(36)  NOT NULL UNIQUE,
    file_name     VARCHAR(256) NOT NULL,
    file_type     VARCHAR(16)  NOT NULL,
    file_size     BIGINT       NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    chunk_count   INT          NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1024),
    uploaded_by   VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
