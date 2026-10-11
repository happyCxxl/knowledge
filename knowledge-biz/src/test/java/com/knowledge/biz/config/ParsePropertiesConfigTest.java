package com.knowledge.biz.config;

import com.knowledge.worker.parser.ParseProperties;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析阈值配置口径：`application.yml` 的 `knowledge.parse` 覆盖 ParseProperties 的全部字段，
 * 且初始值与代码默认值一致（配置面与代码面不许偏）。
 *
 * @author cxxl
 */
class ParsePropertiesConfigTest {

    @Test
    void applicationYmlShouldCoverAllParseProperties() throws Exception {
        Map<String, Object> parse = parseBlock();
        ParseProperties defaults = new ParseProperties();

        for (Field field : ParseProperties.class.getDeclaredFields()) {
            String key = camelToKebab(field.getName());
            assertTrue(parse.containsKey(key), () -> "application.yml 缺配置项: " + key);
            field.setAccessible(true);
            assertEquals(String.valueOf(field.get(defaults)), String.valueOf(parse.get(key)),
                    () -> "配置项与代码默认值不一致: " + key);
        }
    }

    /** `knowledge.parse` 段（读文本时先替换 Maven 资源过滤占位符，SnakeYAML 不接受裸 @） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBlock() throws Exception {
        Path yml = Path.of("src", "main", "resources", "application.yml");
        assertTrue(Files.exists(yml), () -> "找不到配置文件: " + yml.toAbsolutePath());
        String text = Files.readString(yml, StandardCharsets.UTF_8).replaceAll("@[^@\\s]+@", "placeholder");
        Map<String, Object> root = new Yaml().load(text);
        Map<String, Object> knowledge = (Map<String, Object>) root.get("knowledge");
        assertTrue(knowledge != null && knowledge.containsKey("parse"),
                () -> "application.yml 缺 knowledge.parse 段");
        return (Map<String, Object>) knowledge.get("parse");
    }

    /** 驼峰字段名 → yml 的 kebab-case 键名 */
    private String camelToKebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }
}
