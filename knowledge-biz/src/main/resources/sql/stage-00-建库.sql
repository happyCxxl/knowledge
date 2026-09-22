-- ============================================================================
-- 迁移脚本 00：建库（阶段 0 工程基线 + 数据库落地，执行一次）
-- 位置：knowledge-biz/src/main/resources/sql/step-00-建库.sql
--
-- 初始化口径：本目录迁移脚本按文件名序一次执行（Docker 初始化时挂载整个目录），
--   每个脚本对应一个实施环节（docs/steps/step-XX），与环节代码同步提交。
--   全新库：按文件名序执行全部脚本；已建库环境：只执行新增的脚本。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS knowledge
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- Docker 初始化执行本文件时未预选库，需显式 USE
USE knowledge;
