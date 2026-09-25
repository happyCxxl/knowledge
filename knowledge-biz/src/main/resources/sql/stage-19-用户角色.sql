-- ============================================================================
-- 迁移脚本 19：用户角色与令牌版本（阶段 19，执行一次）
-- ============================================================================
-- 说明：
--   1. kb_user 增加 role 字段用于区分权限（ADMIN 管理员 / USER 普通用户）。
--      存量用户统一回落 USER；管理员由既有管理员在用户管理中调整，
--      或自行执行：UPDATE kb_user SET role = 'ADMIN' WHERE username = 'xxx';
--   2. kb_user 增加 token_version：令牌携带签发时的版本号，过滤器每次校验与库值一致，
--      因此改密码/改角色/停用/删除后，已签发的旧令牌立即失效（无需等 7 天过期）。
--   本脚本不 seed 任何账号。
-- ============================================================================

-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

ALTER TABLE kb_user
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色码值：ADMIN 管理员 / USER 普通用户'
        AFTER status;

ALTER TABLE kb_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本：递增即让已签发令牌失效'
        AFTER role;
