-- ============================================================================
-- 迁移脚本 09：切片两表 + 切片策略 seed（阶段 9，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 9：切片策略切片两表 —— 执行一次
-- ----------------------------------------------------------------------------
CREATE TABLE kb_chunk_set
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_result_id         BIGINT       NOT NULL                COMMENT '所属文件结果',
    upstream_product_id    BIGINT       NOT NULL                COMMENT '上游产物（预处理视图产物）',
    chunk_strategy_version VARCHAR(32)  NOT NULL                COMMENT '切片策略版本（版本号字符串）',
    chunk_count            INT          NOT NULL DEFAULT 0      COMMENT '切片数',
    total_chars            INT          NOT NULL DEFAULT 0      COMMENT '总字符数',
    status                 VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    artifact_id            VARCHAR(255) NOT NULL                COMMENT 'ChunkSet 归档 JSON 引用（sha256）',
    create_time            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_fr (file_result_id)
)  COMMENT ='切片产物集合（一次切片策略运行；机器表 append-only）';

CREATE TABLE kb_chunk
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    chunk_set_id       BIGINT       NOT NULL                COMMENT '所属切片集合',
    chunk_id           VARCHAR(64)  NOT NULL                COMMENT '集合内顺序号（集合内唯一）',
    parent_chunk_id    VARCHAR(64)           DEFAULT NULL   COMMENT '父片（父子层级）',
    content            MEDIUMTEXT   NOT NULL                COMMENT '切片内容（normalizedText 口径）',
    content_type       VARCHAR(32)  NOT NULL                COMMENT '内容类型：SECTION/PARAGRAPH/TABLE/IMAGE/FALLBACK',
    title_path         VARCHAR(512)          DEFAULT NULL   COMMENT '标题路径（最多 3 级）',
    source_element_ids varchar(1024)                  DEFAULT NULL   COMMENT '来源元素 ID（溯源）',
    page_range         VARCHAR(1024)          DEFAULT NULL   COMMENT '页码范围（如 1-3；连续段压缩、多段逗号连接，无损不截断）',
    table_ref          VARCHAR(64)           DEFAULT NULL   COMMENT '表格引用（统一文档模型表元素 ID）',
    order_no           INT          NOT NULL DEFAULT 0      COMMENT '集合内顺序',
    char_count         INT          NOT NULL DEFAULT 0      COMMENT '字符数',
    token_count        INT          NOT NULL DEFAULT 0      COMMENT 'Token 估算（字符数÷1.5）',
    strategy_version   VARCHAR(32)           DEFAULT NULL   COMMENT '切片策略版本（冗余）',
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_set (chunk_set_id),
    KEY idx_parent (chunk_set_id, parent_chunk_id)
)  COMMENT ='切片：检索命中的最小单元（机器表 append-only）';
-- ============================================================
-- 切片策略 CHUNK（5 条）
-- ============================================================

-- ① 混合默认（与内置 chunk-hybrid-v1 同参）：段落聚合 + 行级表切片 + 递归兜底
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('CHUNK', 'chunk-hybrid', 'v1',
     '{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"800","softMaxLen":"1000"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"500","overlap":"50"}},"pipeline":{"titlePathMaxLevel":3,"parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":300,"structureOverlap":0,"titleInContent":"ON"}}',
     'ACTIVE');

-- ② 细粒度：小片多、召回精确（对比"片小"对检索的影响）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('CHUNK', 'chunk-fine', 'v1',
     '{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"400","softMaxLen":"600"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"15","groupSize":"2"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"300","overlap":"50"}},"pipeline":{"titlePathMaxLevel":3,"parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":150,"structureOverlap":0,"titleInContent":"ON"}}',
     'ACTIVE');

-- ③ 粗粒度：大片多、上下文强（对比"片大"对召回粗的影响）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('CHUNK', 'chunk-coarse', 'v1',
     '{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"1600","softMaxLen":"2000"}},"table":{"algorithm":"row-group","params":{"groupSize":"5","maxLen":"800"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"800","overlap":"100"}},"pipeline":{"titlePathMaxLevel":3,"parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":500,"structureOverlap":0,"titleInContent":"ON"}}',
     'ACTIVE');

-- ④ 标题边界：按章节成片（超长章节才切）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('CHUNK', 'chunk-title-boundary', 'v1',
     '{"body":{"algorithm":"title-boundary","params":{"maxLen":"3000"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"500","overlap":"50"}},"pipeline":{"titlePathMaxLevel":3,"parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":300,"structureOverlap":0,"titleInContent":"ON"}}',
     'ACTIVE');

-- ⑤ 固定窗口：无视结构硬切（观察窗口边界语义断裂）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('CHUNK', 'chunk-window', 'v1',
     '{"body":{"algorithm":"fixed-window","params":{"len":"500","overlap":"100"}},"table":{"algorithm":"context-merged","params":{"leadMaxLen":"200","groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"fixed-window","params":{"len":"500","overlap":"100"}},"pipeline":{"titlePathMaxLevel":3,"parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":200,"structureOverlap":0,"titleInContent":"ON"}}',
     'ACTIVE');
