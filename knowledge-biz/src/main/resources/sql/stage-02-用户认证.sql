-- ============================================================================
-- 迁移脚本 02：用户表（阶段 2，执行一次）
-- ============================================================================

-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

CREATE TABLE kb_user
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    username    VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
    password    VARCHAR(128) NOT NULL                COMMENT '密码（BCrypt 哈希）',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    del_flag    VARCHAR(1)   NOT NULL DEFAULT '0'    COMMENT '删除标记：0 正常 / 1 已删',
    create_by   VARCHAR(64)           DEFAULT NULL   COMMENT '创建人',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by   VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_del_flag (del_flag)
) COMMENT ='用户：登录账号';
