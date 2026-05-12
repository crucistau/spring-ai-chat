CREATE DATABASE IF NOT EXISTS chatdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chatdb;

-- 将 user 改为 users，避免关键字冲突
CREATE TABLE IF NOT EXISTS `users` (
                                       `id`         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一ID',
                                       `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`   VARCHAR(100) NOT NULL COMMENT '密码哈希值',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    UNIQUE KEY `uk_username` (`username`) -- 显式命名唯一索引
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

CREATE TABLE IF NOT EXISTS `conversation` (
                                              `id`         VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会话唯一ID(UUID)',
    `user_id`    BIGINT       NOT NULL COMMENT '关联的用户ID',
    `title`      VARCHAR(200) NULL COMMENT '会话标题',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '会话最后更新时间',
    INDEX `idx_user_id` (`user_id`),
    -- 添加外键约束，用户删除时级联删除会话
    CONSTRAINT `fk_conversation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
                                              `id`              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '消息唯一ID',
                                              `conversation_id` VARCHAR(36)  NOT NULL COMMENT '关联的会话ID',
    `role`            VARCHAR(20)  NOT NULL COMMENT '消息角色(user/assistant/system)',
    `content`         TEXT         NOT NULL COMMENT '消息内容',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    -- 添加外键约束，会话删除时级联删除消息
    CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息记录表';w