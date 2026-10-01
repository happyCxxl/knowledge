-- ============================================================================
-- 迁移脚本 23：取消默认知识库标记（阶段 23，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- 迁移脚本 23：取消默认知识库标记（执行一次）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 阶段 23：去掉 kb_knowledge_base 的默认库标记、唯一性生成列与唯一键
-- 顺序不可调换：生成列 default_flag_guard 依赖 default_flag，唯一键依赖生成列
-- ----------------------------------------------------------------------------
ALTER TABLE kb_knowledge_base DROP INDEX idx_default_flag;

ALTER TABLE kb_knowledge_base DROP COLUMN default_flag_guard;

ALTER TABLE kb_knowledge_base DROP COLUMN default_flag;
