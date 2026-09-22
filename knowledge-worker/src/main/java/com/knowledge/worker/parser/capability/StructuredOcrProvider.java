package com.knowledge.worker.parser.capability;

import com.knowledge.common.domain.parse.capability.OcrRegion;
import com.knowledge.common.domain.parse.capability.ProviderContext;
import com.knowledge.common.domain.parse.capability.StructuredOcrResult;

/**
 * 结构化 OCR 能力接口（预留扩展，一期无实现）：
 * 实现一个类 + 注册 + 打开配置即接入，主流程零改动。
 * 输出结构经 llm-pool-gateway 返回（字段级契约冻结，值对象见 common.domain.parse）。
 *
 * @author cxxl
 */
public interface StructuredOcrProvider {

    /**
     * 识别图片/页面区域。
     *
     * @param region 待识别区域
     * @param ctx    调用上下文
     * @return 结构化识别结果
     */
    StructuredOcrResult recognize(OcrRegion region, ProviderContext ctx);
}
