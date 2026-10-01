package com.knowledge.biz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.common.dto.response.home.HomeSummaryVO;
import com.knowledge.common.dto.response.index.IndexComboVO;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseStatsVO;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VO 的 JSON 契约测试：**前端按这些字段名与类型消费，改动会静默破坏页面**。
 *
 * <p>本测试用**配置装配出来的** ObjectMapper（而不是裸 {@code new ObjectMapper()}），
 * 因为 ID 转字符串的行为现在由 {@link LongIdJsonConfig} 的全局口径决定 ——
 * 用裸 mapper 测等于绕开被测对象，测不出真实行为。
 *
 * <p>本类原先在 knowledge-common 且用裸 mapper。注解方式被全局配置取代后，
 * 契约已由 knowledge-biz 的配置决定，故测试一并移到这里
 * （依赖方向 biz → common，访问 VO 没有问题）。
 *
 * <p>日期字段的**格式**不在本测试范围内（由 MVC 层 JavaTimeModule 决定，
 * 前端对时间做了容错处理）。
 *
 * @author cxxl
 */
class VoJsonContractTest {

    /** 2^53 - 1：JS 能精确表示的最大整数 */
    private static final long JS_MAX_SAFE = 9007199254740991L;

    private static final long SNOWFLAKE_ID = 2104612193694224386L;

    /** 与 Spring MVC 出口一致的 mapper */
    private final ObjectMapper objectMapper = mapperFromConfig();

    private static ObjectMapper mapperFromConfig() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new LongIdJsonConfig().longIdToStringCustomizer().customize(builder);
        return builder.build();
    }

    private KnowledgeBaseVO fullVo() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(SNOWFLAKE_ID);
        vo.setName("金融研报库");
        vo.setDescription("金融行业 · 研究报告与公告");
        vo.setStatus(1);
        vo.setStrategyBindingEnabled(1);
        vo.setEmbedStrategyVersionId(2104612193694224387L);
        vo.setEmbedStrategyVersion("embed-default-v1");
        vo.setDefaultFlag(0);
        vo.setDocumentCount(7L);
        return vo;
    }

    @Test
    @DisplayName("雪花 ID 以字符串下发，计数保持数字")
    void shouldSerializeSnowflakeIdAsStringAndCountAsNumber() throws Exception {
        String json = objectMapper.writeValueAsString(fullVo());

        // 精度防线：雪花 ID 必须是字符串，否则 JS 端丢精度
        assertTrue(json.contains("\"id\":\"" + SNOWFLAKE_ID + "\""), "id 应为字符串，实际: " + json);
        assertTrue(json.contains("\"embedStrategyVersionId\":\"2104612193694224387\""),
                "策略版本 ID 应为字符串，实际: " + json);
        assertFalse(json.contains("\"id\":" + SNOWFLAKE_ID), "id 不得为数字");

        // 计数是小数值，必须保持数字（字符串化会让前端做比较时静默出错）
        assertTrue(json.contains("\"documentCount\":7"), "documentCount 应为数字，实际: " + json);
    }

    @Test
    @DisplayName("前端依赖的字段名与取值口径")
    void shouldSerializeFieldsConsumedByFrontend() throws Exception {
        String json = objectMapper.writeValueAsString(fullVo());

        // 前端 types/knowledge-base.ts 依赖的字段名
        assertTrue(json.contains("\"name\":\"金融研报库\""), json);
        assertTrue(json.contains("\"description\":"), json);
        assertTrue(json.contains("\"status\":1"), json);
        assertTrue(json.contains("\"embedStrategyVersion\":\"embed-default-v1\""), json);
        assertTrue(json.contains("\"defaultFlag\":0"), json);
    }

    @Test
    @DisplayName("null 保持显式，前端才能用 ?? 回落")
    void shouldKeepNullsExplicitSoFrontendCanFallback() throws Exception {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(1L);
        vo.setStatus(0);
        // 无绑定时策略字段为 null，前端用 ?? 回落展示「未绑定」
        String json = objectMapper.writeValueAsString(vo);

        assertTrue(json.contains("\"embedStrategyVersion\":null"), json);
        assertTrue(json.contains("\"documentCount\":null"), json);
    }

    @Test
    @DisplayName("统计口径：计数字段保持数字（此前被注解盲目字符串化，已修正）")
    void statsCountsShouldStayNumbers() throws Exception {
        KnowledgeBaseStatsVO stats = new KnowledgeBaseStatsVO();
        stats.setKnowledgeBaseCount(12L);
        stats.setEnabledCount(3L);
        // 用大计数验证：即使上千也仍是小数值，不该被字符串化
        stats.setDocumentCount(1284L);

        String json = objectMapper.writeValueAsString(stats);

        assertEquals("{\"knowledgeBaseCount\":12,\"enabledCount\":3,\"documentCount\":1284}", json);
    }

    @Test
    @DisplayName("首页资产速览的计数字段同样是数字")
    void homeSummaryCountsShouldStayNumbers() throws Exception {
        HomeSummaryVO vo = new HomeSummaryVO();
        vo.setKnowledgeBaseCount(6L);
        vo.setEnabledKnowledgeBaseCount(5L);
        vo.setStrategyVersionCount(24L);
        vo.setPreprocessVersionCount(6L);
        vo.setChunkVersionCount(5L);
        vo.setEmbedVersionCount(2L);
        vo.setRetrievalVersionCount(4L);
        vo.setDocumentCount(9L);

        String json = objectMapper.writeValueAsString(vo);

        // 这些字段此前全被 @JsonSerialize(ToStringSerializer) 标注 → 下发成 "6" 这样的字符串。
        // 它们只是"有多少个"的计数，字符串化没有意义且会让前端比较出错。
        for (String expect : List.of("\"knowledgeBaseCount\":6", "\"enabledKnowledgeBaseCount\":5",
                "\"strategyVersionCount\":24", "\"retrievalVersionCount\":4", "\"documentCount\":9")) {
            assertTrue(json.contains(expect), "期望 " + expect + "，实际: " + json);
        }
    }

    @Test
    @DisplayName("集合里的 ID 元素也走同一口径（成员文件 ID 列表）")
    void collectionIdsShouldFollowSameRule() throws Exception {
        IndexComboVO combo = new IndexComboVO();
        combo.setFileResultIds(List.of(SNOWFLAKE_ID, SNOWFLAKE_ID + 1));

        String json = objectMapper.writeValueAsString(combo);

        assertTrue(json.contains("\"fileResultIds\":[\"" + SNOWFLAKE_ID + "\",\"" + (SNOWFLAKE_ID + 1) + "\"]"),
                "数组元素应为字符串，实际: " + json);
    }

    @Test
    @DisplayName("边界：2^53 是数字，2^53+1 起转字符串")
    void boundaryShouldMatchJsSafeInteger() throws Exception {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setDocumentCount(JS_MAX_SAFE);
        assertTrue(objectMapper.writeValueAsString(vo).contains("\"documentCount\":" + JS_MAX_SAFE));

        vo.setDocumentCount(JS_MAX_SAFE + 1);
        assertTrue(objectMapper.writeValueAsString(vo).contains("\"documentCount\":\"" + (JS_MAX_SAFE + 1) + "\""));
    }
}
