-- ============================================================================
-- 迁移脚本 03：知识库管理闭环两表（阶段 3，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- 迁移脚本 01：知识库管理闭环两表（step-01，执行一次）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 阶段 1（step-01）：知识库管理闭环两表 —— 执行一次
-- ----------------------------------------------------------------------------
CREATE TABLE kb_knowledge_base
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    name                       VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description                VARCHAR(512)          DEFAULT NULL COMMENT '业务场景说明',
    status                     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    strategy_binding_enabled   TINYINT      NOT NULL DEFAULT 1 COMMENT '策略绑定开关：1 开启（触发默认走 KB 绑定策略）/ 0 关闭（测评模式，触发必须显式选策略）',
    default_flag               TINYINT      NOT NULL DEFAULT 0 COMMENT '默认知识库标记：1=评测默认库（全库唯一，固定不可停用/删除）',
    default_flag_guard         TINYINT      AS (CASE WHEN default_flag = 1 THEN 1 ELSE NULL END) STORED COMMENT '默认库唯一性辅助生成列（数据库计算，应用勿写）',
    binding_profile_version_id BIGINT                DEFAULT NULL COMMENT '绑定处理策略版本（三件套之一）',
    index_config               varchar(1024)                  DEFAULT NULL COMMENT '索引配置（三件套之一）：自动建索引开关/形态/更新策略',
    default_rule_id            BIGINT                DEFAULT NULL COMMENT '默认检索规则（三件套之一；回退链第二级，kb_pipeline_strategy_version.id，type=RETRIEVAL）',
    published_index_set_id     BIGINT                DEFAULT NULL COMMENT '当前发布索引（一级发布指针；kb_index_set.id）',
    user_id                    BIGINT                DEFAULT NULL COMMENT '创建用户ID',
    del_flag                   VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '删除标记：0 正常 / 1 已删',
    create_by                  VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    create_time                DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by                  VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    update_time                DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_del_flag (del_flag),
    UNIQUE KEY idx_default_flag (default_flag_guard)
) ENGINE = InnoDB
   COMMENT ='知识库：一个业务场景 = 一个知识库';

-- 默认知识库 seed（评测默认库：固定雪花 ID；不绑定策略、各环节显式选策略；固定不可停用/删除）
INSERT INTO kb_knowledge_base
    (id, name, description, status, strategy_binding_enabled, default_flag, default_rule_id)
VALUES (1900000000000000001, '默认知识库',
        '评测场景专用默认库：不绑定任何策略，文件处理与检索各环节显式选择策略；默认检索规则为空（回退引擎基线）',
        1, 0, 1, NULL);

CREATE TABLE kb_audit_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    action_type    VARCHAR(32)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/DISABLE/ENABLE/DELETE/BIND/PUBLISH_INDEX/ROLLBACK_INDEX/RECYCLE_INDEX/RETRIEVAL_RULE_PUBLISH',
    object_type    VARCHAR(32)  NOT NULL COMMENT '对象类型',
    object_id      VARCHAR(64)  NOT NULL COMMENT '对象 ID',
    before_summary VARCHAR(1024)         DEFAULT NULL COMMENT '变更前摘要',
    after_summary  VARCHAR(1024)         DEFAULT NULL COMMENT '变更后摘要',
    user_id        BIGINT                DEFAULT NULL COMMENT '操作用户ID',
    del_flag       VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '删除标记：0 正常 / 1 已删',
    create_by      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    update_time    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_object (object_type, object_id)
) ENGINE = InnoDB
   COMMENT ='操作审计：管理员关键操作留痕（append-only）';
