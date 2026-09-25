package com.knowledge.common.dto.response.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库 VO 的 JSON 契约测试：前端按这些字段名与类型消费，改动会静默破坏页面。
 *
 * <p>本测试用裸 ObjectMapper 覆盖「字段名、雪花 ID 转字符串、null 是否保留」三类契约。
 * 日期字段的**格式**由 MVC 层注册 JavaTimeModule 后的行为决定，不在本测试范围内
 * （knowledge-common 未引入 jackson-datatype-jsr310），前端对时间做了容错处理。
 *
 * @author cxxl
 */
class KnowledgeBaseVoJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KnowledgeBaseVO fullVo() {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        // 雪花 ID 取值超过 JS 安全整数范围，用于验证转字符串确实生效
        vo.setId(9007199254740993L);
        vo.setName("金融研报库");
        vo.setDescription("金融行业 · 研究报告与公告");
        vo.setStatus(1);
        vo.setStrategyBindingEnabled(1);
        vo.setEmbedStrategyVersionId(9007199254740995L);
        vo.setEmbedStrategyVersion("embed-default-v1");
        vo.setDefaultFlag(0);
        vo.setDocumentCount(7L);
        return vo;
    }

    @Test
    void shouldSerializeSnowflakeIdAsString() throws Exception {
        String json = objectMapper.writeValueAsString(fullVo());

        // 精度防线：雪花 ID 必须以字符串下发，否则 JS 端会丢精度
        assertTrue(json.contains("\"id\":\"9007199254740993\""), "id 应为字符串，实际: " + json);
        assertTrue(json.contains("\"embedStrategyVersionId\":\"9007199254740995\""),
                "策略版本 ID 应为字符串，实际: " + json);
        assertFalse(json.contains("\"id\":9007199254740993"), "id 不得为数字");
    }

    @Test
    void shouldSerializeFieldsConsumedByFrontend() throws Exception {
        String json = objectMapper.writeValueAsString(fullVo());

        // 前端 types/knowledge-base.ts 依赖的字段名
        assertTrue(json.contains("\"name\":\"金融研报库\""), json);
        assertTrue(json.contains("\"description\":"), json);
        assertTrue(json.contains("\"status\":1"), json);
        assertTrue(json.contains("\"documentCount\":7"), json);
        assertTrue(json.contains("\"embedStrategyVersion\":\"embed-default-v1\""), json);
        assertTrue(json.contains("\"defaultFlag\":0"), json);
    }

    @Test
    void shouldKeepNullsExplicitSoFrontendCanFallback() throws Exception {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(1L);
        vo.setStatus(0);
        // 无绑定时策略字段为 null，前端用 ?? 回落展示「未绑定」；字段若被省略也会得到 undefined，同样可回落
        String json = objectMapper.writeValueAsString(vo);

        assertTrue(json.contains("\"embedStrategyVersion\":null"), json);
        assertTrue(json.contains("\"documentCount\":null"), json);
    }

    @Test
    void statsFieldsShouldBeStringsForLargeCounts() throws Exception {
        KnowledgeBaseStatsVO stats = new KnowledgeBaseStatsVO();
        stats.setKnowledgeBaseCount(12L);
        stats.setEnabledCount(3L);
        stats.setDocumentCount(1284L);

        String json = objectMapper.writeValueAsString(stats);

        // 前端 types 声明为 string，序列化必须一致
        assertTrue(json.contains("\"knowledgeBaseCount\":\"12\""), json);
        assertTrue(json.contains("\"enabledCount\":\"3\""), json);
        assertTrue(json.contains("\"documentCount\":\"1284\""), json);
    }
}
