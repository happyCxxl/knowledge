-- ============================================================================
-- 迁移脚本 06：解析产物与子步骤两表（阶段 6，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 6：文档解析产物与子步骤两表 —— 执行一次
-- 口径：kb_pipeline_product 不设 uk_artifact——产物行=每次运行一条（平行候选），
--       同一确定性产物可被多条运行行引用（内容去重由产物存储层承担）
-- ----------------------------------------------------------------------------
CREATE TABLE kb_pipeline_product
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_result_id      BIGINT       NOT NULL                COMMENT '所属文件结果',
    stage               VARCHAR(32)  NOT NULL                COMMENT '产出环节（PipelineStage 枚举名）',
    upstream_product_id BIGINT                DEFAULT NULL   COMMENT '上游产物 ID（可空）',
    capability_snapshot varchar(1024)                  DEFAULT NULL   COMMENT '能力/策略版本快照',
    artifact_id         VARCHAR(255) NOT NULL                COMMENT '产物存储引用（sha256 寻址 key）',
    content_hash        CHAR(64)     NOT NULL                COMMENT '产物内容指纹（sha256）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_fr_stage (file_result_id, stage)
)  COMMENT ='阶段产物：本体在产物存储，表内存引用与血缘（机器表 append-only）';

CREATE TABLE kb_pipeline_step_log
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    task_id            BIGINT       NOT NULL                COMMENT '所属处理链任务（kb_pipeline_task.id）',
    step_name          VARCHAR(64)  NOT NULL                COMMENT '子步骤名：文件级路由/原生解析/质量检查…',
    status             VARCHAR(32)  NOT NULL                COMMENT '步骤状态：SUCCESS/FAILED',
    capability_version VARCHAR(64)           DEFAULT NULL   COMMENT '能力/模型版本（如 pdfbox-3.0.4）',
    input_ref          BIGINT                DEFAULT NULL   COMMENT '输入产物引用（可空）',
    output_ref         BIGINT                DEFAULT NULL   COMMENT '输出产物引用（可空）',
    attempt_count      INT          NOT NULL DEFAULT 1      COMMENT '尝试次数',
    started_at         DATETIME(3)           DEFAULT NULL   COMMENT '开始时间',
    finished_at        DATETIME(3)           DEFAULT NULL   COMMENT '结束时间',
    duration           INT                   DEFAULT NULL   COMMENT '耗时（毫秒）',
    warning_count      INT          NOT NULL DEFAULT 0      COMMENT '告警数',
    matched_count      INT          NOT NULL DEFAULT 0      COMMENT '命中数（预处理规则命中次数）',
    changed_count      INT          NOT NULL DEFAULT 0      COMMENT '变更数（实际改写次数）',
    avg_len            INT          NOT NULL DEFAULT 0      COMMENT '平均长度（字符；切片子步骤统计）',
    error              VARCHAR(1024)         DEFAULT NULL   COMMENT '错误信息',
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task (task_id)
)  COMMENT ='子步骤记录：环节内每子步骤一条（机器表 append-only）';
