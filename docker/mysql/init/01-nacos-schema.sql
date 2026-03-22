-- 创建 Nacos 数据库
CREATE DATABASE IF NOT EXISTS nacos DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nacos;

-- Nacos 2.x 官方建表脚本（精简版，完整版见 nacos/conf/mysql-schema.sql）
CREATE TABLE IF NOT EXISTS config_info (
  id           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  data_id      VARCHAR(255) NOT NULL,
  group_id     VARCHAR(128) DEFAULT NULL,
  content      LONGTEXT     NOT NULL,
  md5          VARCHAR(32)  DEFAULT NULL,
  gmt_create   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user     TEXT,
  src_ip       VARCHAR(50)  DEFAULT NULL,
  app_name     VARCHAR(128) DEFAULT NULL,
  tenant_id    VARCHAR(128) DEFAULT '',
  c_desc       VARCHAR(256) DEFAULT NULL,
  c_use        VARCHAR(64)  DEFAULT NULL,
  effect       VARCHAR(64)  DEFAULT NULL,
  type         VARCHAR(64)  DEFAULT NULL,
  c_schema     TEXT,
  encrypted_data_key VARCHAR(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfo_datagrouptenant (data_id, group_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS config_info_beta (
  id           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  data_id      VARCHAR(255) NOT NULL,
  group_id     VARCHAR(128) NOT NULL,
  app_name     VARCHAR(128) DEFAULT NULL,
  content      LONGTEXT     NOT NULL,
  beta_ips     VARCHAR(1024) DEFAULT NULL,
  md5          VARCHAR(32)  DEFAULT NULL,
  gmt_create   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user     TEXT,
  src_ip       VARCHAR(50)  DEFAULT NULL,
  tenant_id    VARCHAR(128) DEFAULT '',
  encrypted_data_key VARCHAR(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfobeta_datagrouptenant (data_id, group_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS config_info_tag (
  id           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  data_id      VARCHAR(255) NOT NULL,
  group_id     VARCHAR(128) NOT NULL,
  tenant_id    VARCHAR(128) DEFAULT '',
  tag_id       VARCHAR(128) NOT NULL,
  app_name     VARCHAR(128) DEFAULT NULL,
  content      LONGTEXT     NOT NULL,
  md5          VARCHAR(32)  DEFAULT NULL,
  gmt_create   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user     TEXT,
  src_ip       VARCHAR(50)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfotag_datagrouptenanttag (data_id, group_id, tenant_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS config_info_aggr (
  id           BIGINT(20)   NOT NULL AUTO_INCREMENT,
  data_id      VARCHAR(255) NOT NULL,
  group_id     VARCHAR(128) NOT NULL,
  datum_id     VARCHAR(255) NOT NULL,
  content      LONGTEXT     NOT NULL,
  gmt_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  app_name     VARCHAR(128) DEFAULT NULL,
  tenant_id    VARCHAR(128) DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfoaggr_datagrouptenantdatum (data_id, group_id, tenant_id, datum_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS his_config_info (
  id            BIGINT(64)   UNSIGNED NOT NULL,
  nid           BIGINT(20)   UNSIGNED NOT NULL AUTO_INCREMENT,
  data_id       VARCHAR(255) NOT NULL,
  group_id      VARCHAR(128) NOT NULL,
  app_name      VARCHAR(128) DEFAULT NULL,
  content       LONGTEXT     NOT NULL,
  md5           VARCHAR(32)  DEFAULT NULL,
  gmt_create    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user      TEXT,
  src_ip        VARCHAR(50)  DEFAULT NULL,
  op_type       CHAR(10)     DEFAULT NULL,
  tenant_id     VARCHAR(128) DEFAULT '',
  encrypted_data_key VARCHAR(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (nid),
  KEY idx_gmt_create (gmt_create),
  KEY idx_gmt_modified (gmt_modified),
  KEY idx_did (data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_info (
  id            BIGINT(20)   NOT NULL AUTO_INCREMENT,
  kp            VARCHAR(128) NOT NULL,
  tenant_id     VARCHAR(128) DEFAULT '',
  tenant_name   VARCHAR(128) DEFAULT '',
  tenant_desc   VARCHAR(256) DEFAULT NULL,
  create_source VARCHAR(32)  DEFAULT NULL,
  gmt_create    BIGINT(20)   NOT NULL,
  gmt_modified  BIGINT(20)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_info_kptenantid (kp, tenant_id),
  KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tenant_capacity (
  id                 BIGINT(20)   UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id          VARCHAR(128) NOT NULL DEFAULT '',
  quota              INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  `usage`            INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_size           INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_aggr_count     INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_aggr_size      INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_history_count  INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  gmt_create         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_capacity (
  id                 BIGINT(20)   UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id           VARCHAR(128) NOT NULL DEFAULT '',
  quota              INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  `usage`            INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_size           INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_aggr_count     INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_aggr_size      INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  max_history_count  INT(10)      UNSIGNED NOT NULL DEFAULT '0',
  gmt_create         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_id (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS permissions (
  role     VARCHAR(50)  NOT NULL,
  resource VARCHAR(255) NOT NULL,
  action   VARCHAR(8)   NOT NULL,
  UNIQUE INDEX uk_role_resource (role, resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS roles (
  username VARCHAR(50) NOT NULL,
  role     VARCHAR(50) NOT NULL,
  UNIQUE INDEX idx_user_role (username, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
  username VARCHAR(50)  NOT NULL PRIMARY KEY,
  password VARCHAR(500) NOT NULL,
  enabled  BOOLEAN      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认 nacos/nacos 账号（BCrypt）
INSERT IGNORE INTO users (username, password, enabled)
  VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);
INSERT IGNORE INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');

-- ── decision 业务库建表 ─────────────────────────────────────────────────────
USE decision;

CREATE TABLE IF NOT EXISTS chat_message (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id VARCHAR(128) NOT NULL                COMMENT '会话ID',
  seq             INT          NOT NULL                COMMENT '消息顺序（0-based）',
  message_type    VARCHAR(16)  NOT NULL                COMMENT 'user / assistant / system',
  content         TEXT         NOT NULL                COMMENT '消息内容',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息持久化表';
