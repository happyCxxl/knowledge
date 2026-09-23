-- ============================================================================
-- 迁移脚本 13：向量两表 + 向量化策略 seed（阶段 13，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 13：向量化向量两表 —— 执行一次
-- 口径：本体在产物存储（EmbeddingSet JSON），两表只存引用/血缘/元数据（机器表 append-only）
-- ----------------------------------------------------------------------------
CREATE TABLE kb_embedding_set
(
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_result_id   BIGINT        NOT NULL COMMENT '所属文件结果',
    chunk_set_ref    BIGINT        DEFAULT NULL COMMENT '上游切片集合引用（kb_chunk_set.id）',
    embedding_set_id VARCHAR(128)  NOT NULL COMMENT '集合ID（es-{chunkSetId}-{strategyVersion}，确定性）',
    strategy_version VARCHAR(64)   NOT NULL COMMENT 'EMBED 策略版本（如 embed-default-v1）',
    model            VARCHAR(128)  NOT NULL COMMENT '模型名（模型目录口径）',
    dimension        INT           NOT NULL COMMENT '向量维度（目录冗余锁定）',
    metric           VARCHAR(16)   NOT NULL COMMENT '度量：COSINE/IP/L2（目录冗余锁定）',
    normalized       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否归一化（目录冗余锁定）',
    record_count     INT           NOT NULL DEFAULT 0 COMMENT '记录数（含复用/跳过）',
    cached_count     INT           NOT NULL DEFAULT 0 COMMENT '复用命中数',
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE',
    artifact_id      VARCHAR(255)  NOT NULL COMMENT 'EmbeddingSet 归档 JSON 引用（sha256）',
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_fr (file_result_id),
    KEY idx_fr_sv (file_result_id, strategy_version)
)  COMMENT ='向量产物集合：一次 EMBED 运行一行；本体在产物存储，本表存引用与血缘';

CREATE TABLE kb_embedding_record
(
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    embedding_set_id BIGINT      NOT NULL COMMENT '所属集合（kb_embedding_set.id）',
    embedding_id     VARCHAR(32) NOT NULL COMMENT '记录ID（emb-0001，集合内唯一）',
    chunk_id         VARCHAR(32) NOT NULL COMMENT '切片ID（Chunk.chunkId）',
    content_type     VARCHAR(32) DEFAULT NULL COMMENT '切片内容类型（ChunkContentType 枚举名）',
    parent_chunk_id  VARCHAR(32) DEFAULT NULL COMMENT '父片ID（扩展预留：父子检索）',
    input_text       MEDIUMTEXT  COMMENT '编码输入文本（=chunk.content 原样）',
    input_text_hash  VARCHAR(64) DEFAULT NULL COMMENT '输入文本指纹（sha256 hex，复用键）',
    token_count      INT         NOT NULL DEFAULT 0 COMMENT 'Token 估算（ceil(字符数÷1.5)）',
    request_id       VARCHAR(64) DEFAULT NULL COMMENT '网关 requestId（复用命中时为空）',
    status           VARCHAR(16) NOT NULL COMMENT '状态：SUCCESS/CACHED/SKIPPED/FAILED',
    cache_hit        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否账本复用命中',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_set (embedding_set_id)
)  COMMENT ='向量记录：一个切片一条；向量本体只进集合文件，不进 DB';
-- ============================================================
-- 向量化策略 EMBED（2 条）
-- ============================================================

-- ① 默认复用：BGE-M3 + 账本复用开（省钱路径）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('EMBED', 'embed-default', 'v1',
     '{"model":"text-embedding-v4","docTemplate":"{content}","queryTemplate":"{query}","batchSize":32,"timeoutMs":30000,"maxRetries":2,"cacheEnabled":"ON","includeParent":"OFF","skipEmpty":"ON","dimension":1024,"metric":"COSINE","normalized":true,"contextWindowTokens":8192,"batchLimit":64}',
     'ACTIVE');

-- ② 强制重算：复用关（对比实验用——观察全量重算 vs 账本复用）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('EMBED', 'embed-nocache', 'v1',
     '{"model":"text-embedding-v4","docTemplate":"{content}","queryTemplate":"{query}","batchSize":16,"timeoutMs":30000,"maxRetries":2,"cacheEnabled":"OFF","includeParent":"OFF","skipEmpty":"ON","dimension":1024,"metric":"COSINE","normalized":true,"contextWindowTokens":8192,"batchLimit":64}',
     'ACTIVE');
