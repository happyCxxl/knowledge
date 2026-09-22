-- ============================================================================
-- 迁移脚本 08：策略版本表 + 预处理策略 seed（阶段 8，执行一次）
-- 自包含执行：USE knowledge（Docker 初始化每个脚本独立会话）
USE knowledge;

-- ----------------------------------------------------------------------------
-- 阶段 8：数据预处理策略版本表 + seed —— 执行一次
-- 口径：全表不可变（编辑=复制新行、停用代替删除）；type 白名单：PREPROCESS/CHUNK/EMBED/RETRIEVAL
-- ----------------------------------------------------------------------------
CREATE TABLE kb_pipeline_strategy_version
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花，应用显式传 id）',
    type            VARCHAR(32)  NOT NULL                COMMENT '策略类型：PREPROCESS/CHUNK/EMBED/RETRIEVAL',
    name            VARCHAR(128) NOT NULL                COMMENT '策略名',
    version         VARCHAR(32)  NOT NULL                COMMENT '版本号',
    config_snapshot varchar(1024)         NOT NULL                COMMENT '配置快照（完整参数）',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_type_name_version (type, name, version)
)  COMMENT ='环节策略版本（不可变，修改即新版本）';
-- ============================================================
-- 预处理策略 PREPROCESS（5 条）
-- ============================================================

-- ① 默认（内置回退同款：七规则默认开，页眉页脚/目录/噪声只标记）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status)
VALUES ('PREPROCESS', 'preproc-default', 'v1',
        '{"rules":{"headerFooter":{"action":"MARK"},"toc":{"action":"MARK","params":{"minLinesPerPage":"3","runMinLength":"3"}},"noise":{"action":"MARK"},"repeat":{"enabled":"ON"},"field":{"enabled":"ON","params":{"amount":"ON","date":"ON","area":"ON","certNo":"ON"}},"tidy":{"enabled":"ON","params":{"whitespace":"ON","punct":"ON","dashes":"ON","bullets":"ON","urls":"ON"}},"encoding":{"enabled":"ON"}},"custom":{"enabled":"ON","rules":[]}}',
        'ACTIVE');

-- ② 标准宽松：七规则全开，页眉页脚/目录/噪声只标记不剔除
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('PREPROCESS', 'preproc-standard', 'v1',
     '{"rules":{"headerFooter":{"action":"MARK"},"toc":{"action":"MARK","params":{"minLinesPerPage":"3","runMinLength":"3"}},"noise":{"action":"MARK"},"repeat":{"enabled":"ON"},"field":{"enabled":"ON","params":{"amount":"ON","date":"ON","area":"ON","certNo":"ON"}},"tidy":{"enabled":"ON","params":{"whitespace":"ON","punct":"ON","dashes":"ON","bullets":"ON","urls":"ON"}},"encoding":{"enabled":"ON"}},"custom":{"enabled":"ON","rules":[]}}',
     'ACTIVE');

-- ③ 严格清洗：页眉页脚/目录/噪声全部剔除 + 自定义规则移除版权行
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('PREPROCESS', 'preproc-strict', 'v1',
     '{"rules":{"headerFooter":{"action":"EXCLUDE"},"toc":{"action":"EXCLUDE","params":{"minLinesPerPage":"2","runMinLength":"2"}},"noise":{"action":"EXCLUDE"},"repeat":{"enabled":"ON"},"field":{"enabled":"ON","params":{"amount":"ON","date":"ON","area":"ON","certNo":"ON"}},"tidy":{"enabled":"ON","params":{"whitespace":"ON","punct":"ON","dashes":"ON","bullets":"ON","urls":"ON"}},"encoding":{"enabled":"ON"}},"custom":{"enabled":"ON","rules":[{"pattern":"版权所有.{0,60}","action":"REMOVE"}]}}',
     'ACTIVE');

-- ④ 最小干预：几乎原样保留（观察"原始文本 vs 清洗后"的差异）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('PREPROCESS', 'preproc-minimal', 'v1',
     '{"rules":{"headerFooter":{"action":"KEEP"},"toc":{"action":"KEEP","params":{"minLinesPerPage":"3","runMinLength":"3"}},"noise":{"action":"MARK"},"repeat":{"enabled":"OFF"},"field":{"enabled":"OFF","params":{"amount":"ON","date":"ON","area":"ON","certNo":"ON"}},"tidy":{"enabled":"OFF","params":{"whitespace":"ON","punct":"ON","dashes":"ON","bullets":"ON","urls":"ON"}},"encoding":{"enabled":"ON"}},"custom":{"enabled":"OFF","rules":[]}}',
     'ACTIVE');

-- ⑤ 目录保留：目录 KEEP、噪声剔除（观察目录入检索流的效果）
INSERT INTO kb_pipeline_strategy_version (type, name, version, config_snapshot, status) VALUES
    ('PREPROCESS', 'preproc-keep-toc', 'v1',
     '{"rules":{"headerFooter":{"action":"EXCLUDE"},"toc":{"action":"KEEP","params":{"minLinesPerPage":"3","runMinLength":"3"}},"noise":{"action":"EXCLUDE"},"repeat":{"enabled":"ON"},"field":{"enabled":"ON","params":{"amount":"ON","date":"ON","area":"ON","certNo":"ON"}},"tidy":{"enabled":"ON","params":{"whitespace":"ON","punct":"ON","dashes":"ON","bullets":"ON","urls":"ON"}},"encoding":{"enabled":"ON"}},"custom":{"enabled":"ON","rules":[]}}',
     'ACTIVE');
