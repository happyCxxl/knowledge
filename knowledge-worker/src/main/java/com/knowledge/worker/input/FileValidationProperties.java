package com.knowledge.worker.input;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件校验配置（knowledge.file 前缀）。
 * Nacos 同名键可覆盖；默认值为一期定稿值。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.file")
public class FileValidationProperties {

    /** 文件大小上限（字节），默认 100MB；超限即停、不再继续读取 */
    private long maxSize = 100L * 1024 * 1024;

    /**
     * 启用格式白名单（FileFormat 枚举名）。
     * 默认只启用数字版 PDF/DOC/DOCX/XLS/XLSX；
     * PPT/PPTX/TXT/MD/CSV 可识别但预留关闭，需要时经 Nacos 放开。
     */
    private List<String> enabledFormats = List.of("PDF", "DOC", "DOCX", "XLS", "XLSX");

    /** 流式校验内存缓冲阈值（字节），默认 10MB；超阈值部分溢写到临时文件，校验完即删 */
    private long tempFileThreshold = 10L * 1024 * 1024;
}
