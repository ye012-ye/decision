-- 系统用户表（JWT 登录）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(64)  NOT NULL DEFAULT '',
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统用户';

-- DEV ONLY 初始账号：admin / admin123
-- 生产部署前请更换此 BCrypt 密文（或删除本条 INSERT，通过独立置备流程创建初始管理员）
INSERT INTO sys_user (username, password, nickname, role, status)
VALUES ('admin', '$2a$10$zeN4j4kVkqQ3ECCFqODRtOvFe69itVyYzQkcA6J30ank9ivaqpW/6', '管理员', 'ADMIN', 1);
