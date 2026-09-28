package com.knowledge.filecenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置：存储后端选择、本地存储根目录与 MinIO 连接/桶名。
 *
 * <p>**两种后端**（{@link #storageType}）：{@code minio}（默认，对象存储）与
 * {@code local}（本地磁盘，开发/演示用）。两者的"桶"语义一致 —— 本地实现把桶名当
 * 子目录，所以 {@code kb_file_object.bucket} 档案字段无需区分后端。
 *
 * <p><b>切换后端前请注意</b>：已写入的对象不会自动迁移，切换后旧对象在新后端里读不到
 * （反过来也一样）。要让存量数据可用，需要先把对象文件复制到新后端的对应位置。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-center")
public class FileCenterConfig {

    /** 存储后端：minio（默认）/ local（本地磁盘） */
    private String storageType = "minio";

    /** 本地存储根目录（storage-type=local 时生效）；相对路径按进程工作目录解析 */
    private String localRoot = "./data/file-center";

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
