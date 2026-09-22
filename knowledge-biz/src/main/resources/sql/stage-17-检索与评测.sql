-- ============================================================================
-- 迁移脚本 17：检索运行记录表 + 检索规则 seed（阶段 17，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 17：检索与评测运行记录 —— 执行一次
-- 口径（快照即证据）：四元组 + 执行时刻结果快照；测试台必记、生产按开关；评测对比回放快照不重跑。
-- ----------------------------------------------------------------------------
CREATE TABLE kb_retrieval_run
(
    id                 BIGINT      NOT NULL COMMENT '主键（雪花）',
    kb_id              BIGINT      NOT NULL COMMENT '知识库 ID',
    version_id         BIGINT      NOT NULL COMMENT '索引版本行 ID',
    version_no         VARCHAR(32) NOT NULL COMMENT '版本号（快照冗余，展示用）',
    rule_id            BIGINT               DEFAULT NULL COMMENT '规则行 ID（kb_pipeline_strategy_version；引擎基线规则无行 ID 时为 NULL）',
    rule_name_version  VARCHAR(64) NOT NULL COMMENT '规则 name-version（快照冗余，展示用）',
    query              TEXT        NOT NULL COMMENT '查询文本',
    result_snapshot    varchar(1024)        NOT NULL COMMENT '结果快照（命中列表全字段，执行时刻快照=评测证据）',
    elapsed_ms         INT         NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    create_time        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_kb_version_rule (kb_id, version_id, rule_id),
    KEY idx_create_time (create_time)
)  COMMENT ='检索运行记录：四元组 + 执行时刻快照（评测原始数据；机器表 append-only）';
-- ============================================================
-- 检索规则 RETRIEVAL（4 条）
-- ============================================================

-- ① 纯全文：BM25 通道，跳过查询向量化
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
('RETRIEVAL', 'fulltext-top10', 'v1',
 '{"channel":"FULLTEXT","fusion":{"mode":"RRF","rrfK":60,"perChannelLimit":50},"preprocess":{"mode":"NONE"},"rerank":{"mode":"NONE"},"postprocess":{"mode":"NONE"},"topK":10,"scoreThreshold":0}',
 'ACTIVE');

-- ② 纯向量：HNSW 通道
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
('RETRIEVAL', 'vector-top10', 'v1',
 '{"channel":"VECTOR","fusion":{"mode":"RRF","rrfK":60,"perChannelLimit":50},"preprocess":{"mode":"NONE"},"rerank":{"mode":"NONE"},"postprocess":{"mode":"NONE"},"topK":10,"scoreThreshold":0}',
 'ACTIVE');

-- ③ 混合 RRF：双通道融合（无后处理）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
('RETRIEVAL', 'hybrid-rrf-k60-top10', 'v1',
 '{"channel":"HYBRID","fusion":{"mode":"RRF","rrfK":60,"perChannelLimit":50},"preprocess":{"mode":"NONE"},"rerank":{"mode":"NONE"},"postprocess":{"mode":"NONE"},"topK":10,"scoreThreshold":0}',
 'ACTIVE');

-- ④ 混合 RRF + 父片展开（引擎基线规则同款）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
('RETRIEVAL', 'hybrid-rrf-k60-top10-parent', 'v1',
 '{"channel":"HYBRID","fusion":{"mode":"RRF","rrfK":60,"perChannelLimit":50},"preprocess":{"mode":"NONE"},"rerank":{"mode":"NONE"},"postprocess":{"mode":"PARENT_EXPAND"},"topK":10,"scoreThreshold":0}',
 'ACTIVE');
