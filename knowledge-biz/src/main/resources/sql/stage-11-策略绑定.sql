-- ============================================================================
-- 迁移脚本 11：知识库-策略绑定表（阶段 11，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 11：策略管理独立菜单与知识库绑定 —— 执行一次
-- 口径：知识库按策略类型绑定一个策略版本（PREPROCESS/CHUNK/EMBED）；
--       解绑为逻辑删除，重绑复用原行（uk 不被占用）。
-- ----------------------------------------------------------------------------
CREATE TABLE kb_strategy_binding
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    knowledge_base_id   BIGINT       NOT NULL COMMENT '知识库 ID',
    strategy_type       VARCHAR(32)  NOT NULL COMMENT '策略类型：PREPROCESS/CHUNK/EMBED',
    strategy_version_id BIGINT       NOT NULL COMMENT '策略版本行 ID（kb_pipeline_strategy_version.id）',
    del_flag            VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '删除标记：0 正常 / 1 已删（解绑）',
    create_by           VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by           VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_kb_type (knowledge_base_id, strategy_type)
)  COMMENT ='知识库-策略绑定：上层应用接入后按知识库直接取用绑定策略';
