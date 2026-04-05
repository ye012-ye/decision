-- MCP 模块建表脚本

CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name       VARCHAR(64)    NOT NULL COMMENT 'MCP 工具名称',
    sql_text        TEXT           NOT NULL COMMENT '执行的 SQL（截断至 4096 字符）',
    operation_type  VARCHAR(16)    NOT NULL COMMENT 'SELECT/INSERT/UPDATE/DELETE',
    status          VARCHAR(16)   NOT NULL COMMENT 'SUCCESS/DENIED/ERROR',
    error_message   VARCHAR(1024)  DEFAULT NULL,
    rows_affected   INT            DEFAULT 0,
    execution_ms    BIGINT         DEFAULT 0,
    session_id      VARCHAR(128)   DEFAULT NULL COMMENT 'Agent 会话 ID',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_name (tool_name),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP SQL 执行审计日志';

CREATE TABLE IF NOT EXISTS mcp_table_whitelist (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name      VARCHAR(128)   NOT NULL UNIQUE COMMENT '白名单表名',
    description     VARCHAR(256)   DEFAULT NULL,
    status          TINYINT        NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_table_name (table_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 表访问白名单';
