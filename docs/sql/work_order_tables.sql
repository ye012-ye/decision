-- 工单表
CREATE TABLE IF NOT EXISTS work_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL UNIQUE COMMENT '工单编号 WOyyyyMMddNNN',
    type            VARCHAR(32)    NOT NULL COMMENT 'ORDER/LOGISTICS/ACCOUNT/TECH_FAULT/CONSULTATION/OTHER',
    priority        VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW/MEDIUM/HIGH/URGENT',
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
    title           VARCHAR(256)   NOT NULL,
    description     TEXT           NOT NULL,
    customer_id     VARCHAR(64)    NOT NULL COMMENT '客户标识（手机号/用户ID）',
    assignee        VARCHAR(64)    DEFAULT NULL COMMENT '处理人',
    assignee_group  VARCHAR(64)    DEFAULT NULL COMMENT '处理组',
    resolution      TEXT           DEFAULT NULL COMMENT '处理结果',
    session_id      VARCHAR(128)   DEFAULT NULL COMMENT '关联对话 session',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at     DATETIME       DEFAULT NULL,
    INDEX idx_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单';

-- 工单操作日志表
CREATE TABLE IF NOT EXISTS work_order_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL COMMENT '关联工单编号',
    action          VARCHAR(32)    NOT NULL COMMENT 'CREATE/ASSIGN/UPDATE_STATUS/ADD_NOTE/CLOSE',
    operator        VARCHAR(64)    NOT NULL COMMENT '操作人',
    content         TEXT           DEFAULT NULL COMMENT '操作内容/备注',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单操作日志';

-- 分配规则表
CREATE TABLE IF NOT EXISTS assignee_rule (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_type   VARCHAR(32)    NOT NULL COMMENT '工单类型',
    assignee_group    VARCHAR(64)    NOT NULL COMMENT '处理组名称',
    assignee          VARCHAR(64)    NOT NULL COMMENT '默认处理人',
    assignee_email    VARCHAR(128)   DEFAULT NULL COMMENT '处理人邮箱',
    status            TINYINT        NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    UNIQUE KEY uk_type (work_order_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单分配规则';

-- 初始分配规则
INSERT INTO assignee_rule (work_order_type, assignee_group, assignee, assignee_email) VALUES
('ORDER',        '订单组',   '订单专员',   'order@example.com'),
('LOGISTICS',    '物流组',   '物流专员',   'logistics@example.com'),
('ACCOUNT',      '用户组',   '用户专员',   'account@example.com'),
('TECH_FAULT',   '技术组',   '技术专员',   'tech@example.com'),
('CONSULTATION', '咨询组',   '咨询专员',   'consult@example.com'),
('OTHER',        '综合组',   '综合专员',   'general@example.com');
