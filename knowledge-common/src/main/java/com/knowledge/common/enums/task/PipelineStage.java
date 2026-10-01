package com.knowledge.common.enums.task;

import java.util.Set;

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
    BUILD_INDEX;

    /** 逐文件执行链五环节（解析 → 组装 → 预处理 → 切片 → 向量化，不含库级 BUILD_INDEX）：
     * 血缘执行树与产物内容接口的白名单同一口径，集中在这里，避免各处各写一份。
     */
    public static final Set<String> FILE_CHAIN_STAGES = Set.of(
            PARSE.name(), STRUCTURE.name(), PREPROCESS.name(), CHUNK.name(), EMBED.name());
}
