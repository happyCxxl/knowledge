package com.knowledge.biz.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 配置导入清单口径：主文件 `spring.config.import` 逐行列出的环节文件必须存在、能被 Spring 的 YAML 加载器读出为合法 YAML，
 * 且键落在文件名对应的域内（文件名与内容域一致）。
 *
 * <p>新增环节：加一个 `config/knowledge-<域>.yml`，在 `application.yml` 的清单里加一行，在本用例的 `DOMAINS` 里登记该文件的键前缀。
 *
 * @author cxxl
 */
class ConfigImportManifestTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static final Path MAIN = RESOURCES.resolve("application.yml");

    private static final Path CONFIG_DIR = RESOURCES.resolve("config");

    private static final String IMPORT = "spring.config.import";

    private static final String CLASSPATH = "classpath:";

    /** 允许整份注释、零属性的环节文件（厂商与路由参数按需再写进来） */
    private static final String EMPTY_ALLOWED = "knowledge-model.yml";

    /** 文件名 → 允许出现的键前缀（登记的前缀都要在该文件里出现，{@link #EMPTY_ALLOWED} 除外） */
    private static final Map<String, List<String>> DOMAINS = new LinkedHashMap<>();

    static {
        DOMAINS.put("knowledge-parse.yml", List.of("knowledge.parse."));
        DOMAINS.put("knowledge-auth.yml", List.of("knowledge.auth."));
        DOMAINS.put("knowledge-file.yml", List.of("knowledge.file.", "file-center."));
        DOMAINS.put("knowledge-task.yml", List.of("knowledge.task."));
        DOMAINS.put("knowledge-retrieval.yml", List.of("retrieval."));
        DOMAINS.put(EMPTY_ALLOWED, List.of("knowledge.model."));
    }

    @TempDir
    Path tmp;

    @Test
    void importedFilesShouldExist() throws Exception {
        List<String> locations = importLocations();
        assertTrue(!locations.isEmpty(), () -> "主文件缺 spring.config.import 清单: " + MAIN.toAbsolutePath()
                + "；修法：在 spring.config 下补 config.import 段。");
        List<String> declared = new ArrayList<>();
        for (String location : locations) {
            assertTrue(location.startsWith(CLASSPATH), () -> "导入清单的条目不是 classpath: 资源: " + location
                    + "（主文件 " + MAIN + "）；修法：写成 classpath:config/knowledge-<域>.yml。");
            Path file = resolve(location);
            assertTrue(Files.exists(file), () -> "导入清单里的文件不存在: " + location + "（实际查找 "
                    + file.toAbsolutePath() + "）；修法：补上该文件，或改清单那一行，或删掉多余的一行。");
            declared.add(file.getFileName().toString());
        }
        assertTrue(Files.isDirectory(CONFIG_DIR), () -> "环节文件目录不存在: " + CONFIG_DIR.toAbsolutePath()
                + "；修法：补 config 目录与其中的环节文件。");
        try (Stream<Path> files = Files.list(CONFIG_DIR)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("knowledge-.*\\.yml")).toList()) {
                String name = file.getFileName().toString();
                assertTrue(declared.contains(name), () -> "环节文件没进导入清单: config/" + name + "（主文件 " + MAIN
                        + " 的 spring.config.import）；修法：在该清单里加一行 classpath:config/" + name + "。");
            }
        }
    }

    @Test
    void importedFilesShouldBeValidYaml() throws Exception {
        for (String location : importLocations()) {
            Path file = resolve(location);
            assertTrue(Files.exists(file), () -> "导入清单里的文件不存在: " + location + "（实际查找 "
                    + file.toAbsolutePath() + "）；修法：补上该文件，或改清单那一行，或删掉多余的一行。");
            try {
                new YamlPropertySourceLoader().load(file.getFileName().toString(), resource(file));
            } catch (IOException | RuntimeException e) {
                fail("导入文件不是合法 YAML: " + location + "（" + file.toAbsolutePath() + "）: " + e.getMessage()
                        + "；修法：按 YAML 语法修正该文件。");
            }
        }
    }

    @Test
    void importedFilesShouldMatchTheirDomain() throws Exception {
        for (String location : importLocations()) {
            Path file = resolve(location);
            String name = file.getFileName().toString();
            List<String> prefixes = DOMAINS.get(name);
            assertTrue(prefixes != null, () -> "新环节文件未登记内容域: " + location + "；修法：在本用例的 DOMAINS 里登记 "
                    + name + " 允许的键前缀，并在 application.yml 的 spring.config.import 清单里加一行。");
            List<String> keys = propertyNames(file);
            for (String key : keys) {
                assertTrue(prefixes.stream().anyMatch(key::startsWith), () -> "键不在文件名对应的域内: " + location + " 的 "
                        + key + "（该文件只允许 " + String.join(" / ", prefixes)
                        + "）；修法：把键搬到对应域的文件，或在本用例的 DOMAINS 里改该文件的前缀。");
            }
            if (EMPTY_ALLOWED.equals(name)) {
                continue;
            }
            assertTrue(!keys.isEmpty(), () -> "导入文件没有任何属性: " + location + "（整份注释只允许出现在 " + EMPTY_ALLOWED
                    + "）；修法：把该环节的键写进该文件。");
            for (String prefix : prefixes) {
                assertTrue(keys.stream().anyMatch(key -> key.startsWith(prefix)), () -> "文件名声明的域在文件里没有键: "
                        + location + " 缺 " + prefix + "；修法：补该域的键，或在本用例的 DOMAINS 里删掉这个前缀。");
            }
        }
    }

    /** 主文件清单里的 `classpath:` 资源（保持清单顺序） */
    private List<String> importLocations() throws IOException {
        List<String> locations = new ArrayList<>();
        for (EnumerablePropertySource<?> source : sources(MAIN)) {
            for (String name : source.getPropertyNames()) {
                if (name.equals(IMPORT) || name.startsWith(IMPORT + "[")) {
                    locations.add(String.valueOf(source.getProperty(name)));
                }
            }
        }
        return locations;
    }

    /** 一份 yml 的全部键（加载器已把嵌套段拍平成点分键） */
    private List<String> propertyNames(Path yml) throws IOException {
        List<String> names = new ArrayList<>();
        for (EnumerablePropertySource<?> source : sources(yml)) {
            names.addAll(List.of(source.getPropertyNames()));
        }
        return names;
    }

    /** 一份 yml 的属性源（与启动时同一条 Spring 加载路径） */
    private List<EnumerablePropertySource<?>> sources(Path yml) throws IOException {
        assertTrue(Files.exists(yml), () -> "找不到配置文件: " + yml.toAbsolutePath());
        List<EnumerablePropertySource<?>> sources = new ArrayList<>();
        for (PropertySource<?> loaded : new YamlPropertySourceLoader().load(yml.getFileName().toString(), resource(yml))) {
            assertTrue(loaded instanceof EnumerablePropertySource, () -> "加载器返回的属性源不可枚举: " + yml);
            sources.add((EnumerablePropertySource<?>) loaded);
        }
        return sources;
    }

    /** 资源形态：读文本、替换 Maven 资源过滤占位符（裸 `@name@` 不是合法 YAML 标量），落临时文件后交给加载器 */
    private Resource resource(Path yml) throws IOException {
        String text = Files.readString(yml, StandardCharsets.UTF_8);
        Path plain = tmp.resolve(yml.getFileName().toString());
        Files.writeString(plain, text.replaceAll("@[^@\\s]+@", "placeholder"), StandardCharsets.UTF_8);
        return new FileSystemResource(plain);
    }

    /** `classpath:` 资源 → 源码树里的文件 */
    private Path resolve(String location) {
        return RESOURCES.resolve(location.replace(CLASSPATH, ""));
    }
}
