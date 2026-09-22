package com.knowledge.common.enums.task;

/**
 * 处理链环节：一次环节触发 = 一条 kb_pipeline_task。
 *
 * @author cxxl
 */
public enum PipelineStage {

    /** 解析 */
    PARSE,

    /** 统一结构组装 */
    STRUCTURE,

    /** 预处理 */
    PREPROCESS,

    /** 切片 */
    CHUNK,

    /** 向量化 */
    EMBED,

    /** 索引构建 */
    BUILD_INDEX
}
