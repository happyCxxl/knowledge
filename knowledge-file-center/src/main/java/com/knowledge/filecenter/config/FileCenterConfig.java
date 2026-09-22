package com.knowledge.filecenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置：MinIO 连接与桶名。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-center")
public class FileCenterConfig {

    /** MinIO 服务端点 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 私有密钥 */
    private String secretKey = "minioadmin";

    /** 文件对象桶名 */
    private String fileBucket = "files";

    /** 内容寻址对象桶名 */
    private String artifactBucket = "artifacts";
}
