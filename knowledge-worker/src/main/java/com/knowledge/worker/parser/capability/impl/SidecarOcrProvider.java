package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.capability.OcrRegion;
import com.knowledge.common.domain.parse.capability.ProviderContext;
import com.knowledge.common.domain.parse.capability.StructuredOcrResult;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import com.knowledge.worker.parser.capability.StructuredOcrProvider;
import com.knowledge.worker.parser.capability.http.CapabilitySidecarClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 结构化 OCR 的 sidecar 实现：把待识别区域按契约 POST 到 sidecar 的 `/ocr`，响应即
 * {@link StructuredOcrResult}（逐行文本 + 包围盒 + 置信度 + 模型名与版本）。
 * 开关关闭、区域为空或调用失败返回 null，主流程保持原样（图片只记引用与图注）。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class SidecarOcrProvider implements StructuredOcrProvider {

    private final CapabilityProperties properties;
    private final CapabilitySidecarClient client;

    @Override
    public StructuredOcrResult recognize(OcrRegion region, ProviderContext ctx) {
        if (!properties.isOcrEnabled() || NullUtil.isNull(region)) {
            return null;
        }
        return client.post(CapabilitySidecarClient.OCR_PATH, region, StructuredOcrResult.class);
    }
}
