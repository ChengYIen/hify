-- ============================================================
-- V1 — 用户表
-- MVP 阶段从简：只有 id / username / password / role
-- ============================================================

CREATE TABLE IF NOT EXISTS hify_user (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL COMMENT '用户名',
    password     VARCHAR(255) NOT NULL COMMENT '密码(bcrypt)',
    display_name VARCHAR(64)  DEFAULT NULL COMMENT '显示名',
    role         VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN / USER',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
