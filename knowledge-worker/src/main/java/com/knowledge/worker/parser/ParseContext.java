package com.knowledge.worker.parser;

import com.knowledge.common.domain.input.FileReference;
import lombok.Data;

import java.io.InputStream;

/**
 * 解析上下文（解析机制的执行上下文，留在 worker：持有输入流与解析配置引用）。
 * 输入流由调用方（biz）负责关闭，解析器不关闭流。
 *
 * @author cxxl
 */
@Data
public class ParseContext {

    /** 文件结果 ID（血缘起点，落 resultId） */
    private Long fileResultId;

    /** 文件引用（文档输入环节建档数据） */
    private FileReference fileRef;

    /** 文件流（调用方关闭） */
    private InputStream inputStream;

    /** 解析阈值配置 */
    private ParseProperties properties;
}
