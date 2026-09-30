-- ============================================================================
-- 迁移脚本 21：用户信息扩展（阶段 21，执行一次）
-- ============================================================================
-- 说明：
--   1. kb_user 增加 display_name（真实姓名）、email（邮箱）、phone（手机号）。
--   2. 邮箱与手机号不设唯一约束，登录凭据仍只有 username。
--   3. 不回填存量数据：display_name 缺省为空串，由各账号自行完善。
-- ============================================================================

-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

ALTER TABLE kb_user
    ADD COLUMN display_name VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '真实姓名' AFTER username,
    ADD COLUMN email        VARCHAR(128) DEFAULT NULL     COMMENT '邮箱'   AFTER display_name,
    ADD COLUMN phone        VARCHAR(32)  DEFAULT NULL     COMMENT '手机号' AFTER email;
