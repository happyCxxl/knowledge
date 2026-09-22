-- ============================================================================
-- 迁移脚本 04：文件档案表 + 文档输入四表（阶段 4，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 文件档案表：上传文件元数据（append-only，与其余业务表同库同前缀）
-- ----------------------------------------------------------------------------
USE knowledge;

CREATE TABLE kb_file_object
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_id     VARCHAR(64)  NOT NULL                COMMENT '文件 ID（雪花字符串，MinIO 对象键）',
    file_name   VARCHAR(255) NOT NULL                COMMENT '文件名',
    mime_type   VARCHAR(128)          DEFAULT NULL   COMMENT 'MIME 类型',
    file_size   BIGINT                DEFAULT NULL   COMMENT '文件大小（字节）',
    sha256      CHAR(64)     NOT NULL                COMMENT '内容指纹（sha256）',
    bucket      VARCHAR(64)  NOT NULL                COMMENT '存储桶名',
    object_key  VARCHAR(128) NOT NULL                COMMENT '对象键',
    create_by   VARCHAR(64)           DEFAULT NULL   COMMENT '上传人',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_id (file_id)
) COMMENT ='文件档案：上传文件元数据（append-only）';

-- ----------------------------------------------------------------------------
-- 文档输入四表 —— 执行一次
-- ----------------------------------------------------------------------------
CREATE TABLE kb_source_file
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_id        VARCHAR(64)  NOT NULL                COMMENT '文件 ID',
    sha256         CHAR(64)     NOT NULL                COMMENT '文件内容指纹（输入身份）',
    file_name      VARCHAR(255) NOT NULL                COMMENT '文件名',
    mime_type      VARCHAR(128)          DEFAULT NULL   COMMENT 'MIME 类型',
    file_size      BIGINT                DEFAULT NULL   COMMENT '文件大小（字节）',
    input_snapshot varchar(1024)                  DEFAULT NULL   COMMENT '文件档案快照（JSON 文本）',
    user_id        BIGINT                DEFAULT NULL   COMMENT '上传用户ID',
    create_by      VARCHAR(64)           DEFAULT NULL   COMMENT '创建人（=提交人）',
    create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_file_id (file_id),
    KEY idx_sha256 (sha256)
)  COMMENT ='来源文件：文件引用与指纹（append-only）';

CREATE TABLE kb_file_result
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    knowledge_base_id BIGINT      NOT NULL                COMMENT '所属知识库',
    source_file_id    BIGINT      NOT NULL                COMMENT '原始文件引用',
    user_id           BIGINT               DEFAULT NULL   COMMENT '创建用户ID',
    del_flag          VARCHAR(1)  NOT NULL DEFAULT '0'    COMMENT '删除标记：0 正常 / 1 已删',
    deleted_at        DATETIME(3)          DEFAULT NULL   COMMENT '逻辑删除时间',
    create_by         VARCHAR(64)          DEFAULT NULL   COMMENT '创建人',
    create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by         VARCHAR(64)          DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME(3)          DEFAULT NULL   COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_kb (knowledge_base_id),
    KEY idx_source (source_file_id),
    KEY idx_del_flag (del_flag)
)  COMMENT ='文件结果：一次提交一行（同文件重复提交 = 多行，复用 kb_source_file）';

CREATE TABLE kb_submit_log
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    knowledge_base_id BIGINT       NOT NULL                COMMENT '目标知识库',
    request_id        VARCHAR(64)  NOT NULL                COMMENT '幂等键',
    file_id           VARCHAR(64)  NOT NULL                COMMENT '文件 ID',
    sha256            CHAR(64)     NOT NULL                COMMENT '文件指纹（校验失败存空串占位）',
    file_name         VARCHAR(255) NOT NULL                COMMENT '文件名',
    file_result_id    BIGINT                DEFAULT NULL   COMMENT '建档回填的文件结果 ID（校验失败为空）',
    status            VARCHAR(16)  NOT NULL                COMMENT '提交结果：PASS / FAIL',
    fail_reason       VARCHAR(512)          DEFAULT NULL   COMMENT '失败原因',
    user_id           BIGINT                DEFAULT NULL   COMMENT '提交用户ID',
    del_flag          VARCHAR(1)   NOT NULL DEFAULT '0'    COMMENT '删除标记：append-only 恒 0',
    create_by         VARCHAR(64)           DEFAULT NULL   COMMENT '创建人（=提交人）',
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
    update_by         VARCHAR(64)           DEFAULT NULL   COMMENT '更新人',
    update_time       DATETIME(3)           DEFAULT NULL   COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_request_id (request_id),
    KEY idx_kb_file (knowledge_base_id, file_id)
)  COMMENT ='提交日志：每次提交一条，无论成败';

-- ----------------------------------------------------------------------------
-- 处理链任务表 —— 随文档输入阶段建表（机器表 append-only）
-- 口径：file_result_id 可空（BUILD_INDEX 为知识库级任务）；product_id 成功运行回写产物引用
-- ----------------------------------------------------------------------------
CREATE TABLE kb_pipeline_task
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    file_result_id      BIGINT               DEFAULT NULL   COMMENT '所属文件结果（BUILD_INDEX 知识库级任务为空）',
    stage               VARCHAR(32) NOT NULL                COMMENT '环节：PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED/BUILD_INDEX',
    upstream_product_id BIGINT               DEFAULT NULL   COMMENT '上游产物 ID（可空）',
    strategy_snapshot   varchar(1024)                 DEFAULT NULL   COMMENT '环节策略快照（触发时固定）',
    product_id          BIGINT               DEFAULT NULL   COMMENT '成功运行回写的产物 ID（kb_pipeline_product.id）',
    status              VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '状态：QUEUED/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELLED',
    retry_count         INT         NOT NULL DEFAULT 0      COMMENT '重试次数',
    error_code          VARCHAR(64)          DEFAULT NULL   COMMENT '错误码',
    error_msg           VARCHAR(1024)        DEFAULT NULL   COMMENT '错误信息',
    create_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    started_at          DATETIME(3)          DEFAULT NULL   COMMENT '开始执行时间',
    finished_at         DATETIME(3)          DEFAULT NULL   COMMENT '结束时间',
    PRIMARY KEY (id),
    KEY idx_fr_stage (file_result_id, stage),
    KEY idx_status (status)
)  COMMENT ='处理链任务：一次触发一条任务（机器表 append-only）';
