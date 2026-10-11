package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.capability.LayoutResult;
import com.knowledge.common.domain.parse.capability.PageImage;
import com.knowledge.common.domain.parse.capability.ProviderContext;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import com.knowledge.worker.parser.capability.LayoutProvider;
import com.knowledge.worker.parser.capability.http.CapabilitySidecarClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 版面分析 sidecar 实现：把页面图像按契约 POST 到 sidecar 的 `/layout`，响应即 {@link LayoutResult}
 * （区域分类 + 阅读顺序 + 模型名与版本）。开关关闭、地址未配置或调用失败返回 null，主流程按内置几何口径输出。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class SidecarLayoutProvider implements LayoutProvider {

    private final CapabilityProperties properties;
    private final CapabilitySidecarClient client;

    @Override
    public LayoutResult analyze(PageImage page, ProviderContext ctx) {
        if (!properties.isLayoutEnabled() || NullUtil.isNull(page)) {
            return null;
        }
        return client.post(CapabilitySidecarClient.LAYOUT_PATH, page, LayoutResult.class);
    }
}
