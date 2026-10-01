package com.knowledge.biz.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Long 类型的全局 JSON 序列化口径：**超过 JS 安全整数范围才输出字符串**。
 *
 * <h2>为什么需要它</h2>
 * 雪花 ID 有 19 位（约 2.1e18），而 JavaScript 的 {@code Number.MAX_SAFE_INTEGER} 只有
 * 2^53-1（约 9.0e15）。ID 若以裸数字下发，前端 {@code JSON.parse} 会把末几位抹成 0 ——
 * 实测 {@code 2104612193694224386} 被读成 {@code 2104612193694220000}。
 * 这个失真不是显示问题：前端把 ID 回传给后续接口时，后端查不到该行，
 * 报「知识库不存在」「运行记录不存在」这类看起来毫不相干的错误。
 *
 * <h2>为什么不用「所有 Long 转字符串」</h2>
 * 那会把计数、大小、耗时一并变成字符串（如"有 6 个知识库"下发成 {@code "6"}），
 * 前端拿到字符串做比较/求和会静默出错。**按阈值区分**才是正确口径：
 * <ul>
 *   <li>雪花 ID ≈ 2.1e18 &gt; 2^53 → 字符串；</li>
 *   <li>计数 / 字节数 / 耗时 / 分页号 都是小数值 → 保持数字。</li>
 * </ul>
 * 两者的量级天然分开，所以阈值判定不需要逐字段判断语义。
 *
 * <h2>为什么放在这里而不是给字段加注解</h2>
 * 之前是逐字段 {@code @JsonSerialize(using = ToStringSerializer.class)}，29 个 VO 里散着
 * 65 处 —— **而且已经漏过两次**（知识库创建接口返回裸 {@code Long}、检索包的 4 个 VO 全漏），
 * 每次都要等前端接上才发现。更糟的是它加得盲目：{@code HomeSummaryVO} 的**计数字段**
 * 也被字符串化了。全局配置让"默认就对"，不再依赖谁记得加注解。
 *
 * <h2>影响范围</h2>
 * 只作用于 Spring MVC 自动配置的那个 {@code ObjectMapper}（HTTP 出入参），
 * 不影响内部自己 new 的 ObjectMapper、也不影响 worker / vector 模块。
 * <p>入参方向不需要额外处理：前端把 ID 当字符串传回来时，Jackson 反序列化到
 * {@code Long} 字段本来就能正确转换（已实测）。
 *
 * @author cxxl
 */
@Configuration
public class LongIdJsonConfig {

    /**
     * JS 安全整数上界：2^53 - 1 = 9007199254740991。
     *
     * <p>超过它的整数在 JS 里无法精确表示，必须转字符串。
     */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("long-id-to-string");
            // 同时覆盖包装类型与基本类型：VO 字段用 Long，但集合元素、
            // 以及未来可能的 long 字段都走同一口径
            module.addSerializer(Long.class, new ThresholdLongSerializer());
            module.addSerializer(Long.TYPE, new ThresholdLongSerializer());
            builder.modulesToInstall(module);
        };
    }

    /**
     * 阈值判定序列化器：值在 JS 安全范围内输出数字，超出则输出字符串。
     *
     * <p>负值取绝对值比较（ID 不会为负，但这样更不容易出现意外）。
     */
    static final class ThresholdLongSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (Math.abs(value) <= JS_MAX_SAFE_INTEGER) {
                gen.writeNumber(value);
            } else {
                // 不用 writeString(String.valueOf(value))：直接写字符串字面量，
                // 避免任何本地化/格式化介入
                gen.writeString(value.toString());
            }
        }
    }
}
