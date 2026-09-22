package com.knowledge.worker.parser.impl.parsers;

import com.knowledge.worker.parser.ParseContext;

import java.io.IOException;

/**
 * 解析输入流公共口径（package-private，各解析器共用）：全量读入内存
 * （POI/PDFBox 均需全量加载）；失败抛运行时异常由管线记 PARSE_CORRUPTED。
 *
 * @author cxxl
 */
final class ParserStreamSupport {

    private ParserStreamSupport() {
    }

    /** 输入流全量读入内存；失败抛运行时异常由管线记 PARSE_CORRUPTED。 */
    static byte[] readAll(ParseContext context) {
        try {
            return context.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取文件流失败: " + e.getMessage(), e);
        }
    }
}
