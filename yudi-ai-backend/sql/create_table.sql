-- 创建数据库（若不存在）
CREATE DATABASE IF NOT EXISTS yudi_ai
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 使用数据库
USE yudi_ai;

-- 表一：用户表 user
CREATE TABLE IF NOT EXISTS user
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    user_name    VARCHAR(255) NOT NULL COMMENT '用户昵称',
    user_account VARCHAR(255) NOT NULL UNIQUE COMMENT '用户账号（邮箱）',
    user_avatar  VARCHAR(500) DEFAULT NULL COMMENT '用户头像URL',
    status       TINYINT      DEFAULT 1 COMMENT '用户状态：1-正常，0-禁用',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '用户表';

-- 表二：对话记忆表 conversation_memory
CREATE TABLE IF NOT EXISTS conversation_memory
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    conversation_id    VARCHAR(36) NOT NULL COMMENT '会话ID（UUID）',
    user_id            BIGINT      NOT NULL COMMENT '用户ID',
    conversation_round INT         NOT NULL COMMENT '对话轮次',
    user_input         TEXT COMMENT '用户输入内容',
    ai_response        TEXT COMMENT 'AI回复内容',
    conversation_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间',
    FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '对话记忆表';
