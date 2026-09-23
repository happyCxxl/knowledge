package com.knowledge.worker.chunking.strategy;

import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.common.enums.chunk.ChunkRoute;
import com.knowledge.common.enums.chunk.PipelineKey;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 切片策略快照（结构化模型）：类型/名称/版本 + 四路算法配置（routes）+ 流程层设置（pipeline）。
 * 触发时快照进任务，执行只用快照（可复现）；解析时由 {@link ChunkAlgorithmSpec#normalize} 补全缺省路由与默认参数。
 * 旧扁平格式（parentChild 单键平铺）不再支持。
 *
 * @author cxxl
 */
@Data
public class ChunkStrategy {

    /** 策略类型 */
    public static final String TYPE = "CHUNK";

    /** 内置默认策略名/版本（库内无启用版本时回退） */
    public static final String BUILTIN_NAME = "chunk-hybrid";
    public static final String BUILTIN_VERSION = "v1";

    /** 流程层开关值（JSON 契约） */
    public static final String ON = "ON";
    public static final String OFF = "OFF";

    /** 策略类型（CHUNK） */
    private String type = TYPE;

    /** 策略名 */
    private String name;

    /** 版本号 */
    private String version;

    /** 四路算法配置（键 = ChunkRoute.key()） */
    private Map<String, ChunkRouteConfig> routes = new HashMap<>();

    /** 流程层设置（键 = PipelineKey.key()） */
    private Map<String, String> pipeline = new HashMap<>();

    /** 取某一路配置（缺失返回 null，调用方兜底） */
    public ChunkRouteConfig route(ChunkRoute route) {
        return routes == null || route == null ? null : routes.get(route.key());
    }

    /** 某一路算法（解析后通常存在；键未识别返回 null，调用方兜底） */
    public ChunkAlgorithm routeAlgorithm(ChunkRoute route) {
        ChunkRouteConfig config = route(route);
        return config == null || StrUtil.isBlank(config.getAlgorithm())
                ? null : ChunkAlgorithm.of(route, config.getAlgorithm());
    }

    /** 流程层字符串值（缺失回退默认） */
    public String pipelineValue(PipelineKey key, String defaultValue) {
        String value = pipeline == null || key == null ? null : pipeline.get(key.key());
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 流程层开关判定（ON 大小写不敏感） */
    public boolean pipelineOn(PipelineKey key) {
        return ON.equalsIgnoreCase(pipelineValue(key, OFF));
    }

    /** 流程层数值读取（缺失/非法回退默认） */
    public int pipelineInt(PipelineKey key, int defaultValue) {
        String value = pipelineValue(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 完整版本串（name-version，如 chunk-hybrid-v1） */
    public String fullVersion() {
        return name + "-" + version;
    }
}
