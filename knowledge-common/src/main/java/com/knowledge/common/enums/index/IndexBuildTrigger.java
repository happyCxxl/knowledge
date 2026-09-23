package com.knowledge.common.enums.index;

/**
 * 索引构建触发类型（step-13 B08）：
 * NEW=知识库首建、INCREMENT=文件产物就绪增量（文件即版本）、REBUILD=全量重建、COMPENSATE=回退补齐。
 *
 * @author cxxl
 */
public enum IndexBuildTrigger {

    /** 首建（知识库首批文件） */
    NEW,

    /** 增量（新文件产物就绪） */
    INCREMENT,

    /** 全量重建（策略/模型/维度变化） */
    REBUILD,

    /** 回退补齐（旧口径重跑差异文件） */
    COMPENSATE
}
