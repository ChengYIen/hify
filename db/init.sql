-- ============================================================
-- Hify 数据库初始化脚本
-- 适用：MySQL 8.0+
-- 使用：mysql -uroot -p < db/init.sql
-- ============================================================

-- ---------- 创建数据库 ----------
CREATE DATABASE IF NOT EXISTS hify_dev
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---------- 用户表 ----------
-- MVP 阶段表结构从简，MyBatis-Plus 自动建表时追加字段
-- 这里只建库，表结构由后端启动时自动同步（ddl-auto: update）

-- 如果你需要手动建表，取消下方注释：

/*
USE hify_dev;

CREATE TABLE IF NOT EXISTS hify_user (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码(bcrypt)',
    display_name VARCHAR(64) DEFAULT NULL COMMENT '显示名',
    role        VARCHAR(32)  DEFAULT 'USER' COMMENT '角色',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
*/
