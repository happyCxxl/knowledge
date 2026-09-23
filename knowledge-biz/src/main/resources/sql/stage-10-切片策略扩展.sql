-- ============================================================================
-- 迁移脚本 10：切片策略扩展（阶段 10，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 10：CHUNK seed 快照格式修正 —— 执行一次
-- 背景：阶段 9 落库的 5 条 CHUNK seed 为旧格式（body/table/image/fallback 顶层平铺），
-- 与 ChunkStrategy 模型（{"routes":{...},"pipeline":{...}}）不一致；
-- 解析时未知键被忽略 → routes 落空 → 全部回退默认算法，多策略对比失效。
-- 本脚本按 name+version 幂等 UPDATE 为 routes 包裹格式（算法与参数值不变，pipeline 数值统一字符串）。
-- ----------------------------------------------------------------------------

-- ① 混合默认（与内置 chunk-hybrid-v1 同参）：段落聚合 + 行级表切片 + 递归兜底
UPDATE kb_pipeline_strategy_version SET config_snapshot =
    '{"routes":{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"800","softMaxLen":"1000"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"500","overlap":"50"}}},"pipeline":{"titlePathMaxLevel":"3","parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":"300","structureOverlap":"0","titleInContent":"ON"}}'
WHERE type = 'CHUNK' AND name = 'chunk-hybrid' AND version = 'v1';

-- ② 细粒度：小片多、召回精确（对比"片小"对检索的影响）
UPDATE kb_pipeline_strategy_version SET config_snapshot =
    '{"routes":{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"400","softMaxLen":"600"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"15","groupSize":"2"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"300","overlap":"50"}}},"pipeline":{"titlePathMaxLevel":"3","parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":"150","structureOverlap":"0","titleInContent":"ON"}}'
WHERE type = 'CHUNK' AND name = 'chunk-fine' AND version = 'v1';

-- ③ 粗粒度：大片多、上下文强（对比"片大"对召回粗的影响）
UPDATE kb_pipeline_strategy_version SET config_snapshot =
    '{"routes":{"body":{"algorithm":"paragraph-aggregate","params":{"targetMaxLen":"1600","softMaxLen":"2000"}},"table":{"algorithm":"row-group","params":{"groupSize":"5","maxLen":"800"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"800","overlap":"100"}}},"pipeline":{"titlePathMaxLevel":"3","parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":"500","structureOverlap":"0","titleInContent":"ON"}}'
WHERE type = 'CHUNK' AND name = 'chunk-coarse' AND version = 'v1';

-- ④ 标题边界：按章节成片（超长章节才切）
UPDATE kb_pipeline_strategy_version SET config_snapshot =
    '{"routes":{"body":{"algorithm":"title-boundary","params":{"maxLen":"3000"}},"table":{"algorithm":"row-slice","params":{"groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"recursive-length","params":{"len":"500","overlap":"50"}}},"pipeline":{"titlePathMaxLevel":"3","parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":"300","structureOverlap":"0","titleInContent":"ON"}}'
WHERE type = 'CHUNK' AND name = 'chunk-title-boundary' AND version = 'v1';

-- ⑤ 固定窗口：无视结构硬切（观察窗口边界语义断裂）
UPDATE kb_pipeline_strategy_version SET config_snapshot =
    '{"routes":{"body":{"algorithm":"fixed-window","params":{"len":"500","overlap":"100"}},"table":{"algorithm":"context-merged","params":{"leadMaxLen":"200","groupThreshold":"30","groupSize":"3"}},"image":{"algorithm":"caption-placeholder"},"fallback":{"algorithm":"fixed-window","params":{"len":"500","overlap":"100"}}},"pipeline":{"titlePathMaxLevel":"3","parentChild":"ON","tableInBodyFlow":"OFF","minMergeLen":"200","structureOverlap":"0","titleInContent":"ON"}}'
WHERE type = 'CHUNK' AND name = 'chunk-window' AND version = 'v1';
