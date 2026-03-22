CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    seq             INT          NOT NULL,
    message_type    VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);
