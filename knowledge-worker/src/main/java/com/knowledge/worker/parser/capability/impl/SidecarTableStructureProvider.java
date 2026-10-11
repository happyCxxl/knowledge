package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.capability.ProviderContext;
import com.knowledge.common.domain.parse.capability.TableRegion;
import com.knowledge.common.domain.parse.capability.TableResult;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import com.knowledge.worker.parser.capability.TableStructureProvider;
import com.knowledge.worker.parser.capability.http.CapabilitySidecarClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 表格结构识别 sidecar 实现：把表格区域按契约 POST 到 sidecar 的 `/table`，响应即 {@link TableResult}。
 * 开关关闭、地址未配置或调用失败返回 null，主流程按「能力失败」走内置降级并留痕。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class SidecarTableStructureProvider implements TableStructureProvider {

    private final CapabilityProperties properties;
    private final CapabilitySidecarClient client;

    @Override
    public TableResult recognize(TableRegion region, ProviderContext ctx) {
        if (!properties.isTableEnabled() || NullUtil.isNull(region)) {
            return null;
        }
        return client.post(CapabilitySidecarClient.TABLE_PATH, region, TableResult.class);
    }
}
