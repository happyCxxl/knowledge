-- ============================================================================
-- 迁移脚本 16：索引集合与索引版本两表（阶段 16，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 16：索引构建与发布两表 —— 执行一次
-- 口径（2026-09 策略集合模型定稿）：一个组合 = 一个 Milvus 集合 kb_{kbId}_{versionNo}
-- （集合即边界，行内无 version/kb_id 字段）；发布两级指针 = kb_index_set.current_published_version_id
-- （二级）+ kb_knowledge_base.published_index_set_id（一级）；机器表 append-only、雪花主键。
-- ----------------------------------------------------------------------------
CREATE TABLE kb_index_set
(
    id                           BIGINT      NOT NULL COMMENT '主键（雪花）',
    knowledge_base_id            BIGINT      NOT NULL COMMENT '所属知识库',
    current_published_version_id BIGINT               DEFAULT NULL COMMENT '当前发布版本 ID（二级发布指针；检索环节读取）',
    create_time                  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_kb (knowledge_base_id)
) ENGINE = InnoDB
   COMMENT ='索引集合：一知识库一行，承载二级发布指针';

CREATE TABLE kb_index_version
(
    id             BIGINT        NOT NULL COMMENT '主键（雪花）',
    index_set_id   BIGINT        NOT NULL COMMENT '所属索引集合',
    version_no     VARCHAR(32)   NOT NULL COMMENT '版本号（v1、v2…；= 组合注册序号，集合名 kb_{kbId}_{versionNo} 由它构成）',
    combo_snapshot varchar(1024)          NOT NULL COMMENT '组合快照（fileScope/shape/stageStrategies：stage → 策略 name-version 映射）',
    chunk_count    INT           NOT NULL DEFAULT 0 COMMENT '纳入片数（活账本：随追加持续更新）',
    vector_count   INT           NOT NULL DEFAULT 0 COMMENT '纳入向量数（活账本）',
    status         VARCHAR(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/BUILDING/READY/ONLINE/FAILED/RETIRED',
    default_rule_id BIGINT                DEFAULT NULL COMMENT '默认检索规则（kb_pipeline_strategy_version.id，type=RETRIEVAL；空 → kb 默认 → 引擎基线）',
    build_error    VARCHAR(1024)          DEFAULT NULL COMMENT '失败原因（完整性缺口/维度不一致/一致性失败/写失败）',
    task_id        BIGINT                 DEFAULT NULL COMMENT '构建任务 ID（kb_pipeline_task，stage=BUILD_INDEX）',
    validated_at   DATETIME(3)            DEFAULT NULL COMMENT '验证通过时间',
    published_at   DATETIME(3)            DEFAULT NULL COMMENT '发布时间',
    published_by   VARCHAR(64)            DEFAULT NULL COMMENT '发布人',
    retired_at     DATETIME(3)            DEFAULT NULL COMMENT '退役时间',
    retired_by     VARCHAR(64)            DEFAULT NULL COMMENT '退役人',
    create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_set_version (index_set_id, version_no)
)  COMMENT ='索引版本行：组合快照 + 状态机 + 发布/退役留痕';

-- ----------------------------------------------------------------------------
-- 阶段 16 追加：kb_file_result.owner —— 用户归属（检索强制过滤口径，一期统一 ADMIN）
-- ----------------------------------------------------------------------------
ALTER TABLE kb_file_result
    ADD COLUMN owner VARCHAR(64) NOT NULL DEFAULT 'ADMIN' COMMENT '用户归属（检索强制过滤口径，一期统一 ADMIN）' AFTER knowledge_base_id,
    ADD KEY idx_owner (owner);
