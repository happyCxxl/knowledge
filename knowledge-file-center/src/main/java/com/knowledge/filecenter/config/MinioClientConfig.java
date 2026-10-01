package com.knowledge.filecenter.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配：构建客户端并确保所需桶存在。
 *
 * <p>与 {@link com.knowledge.filecenter.provider.MinioStorageProvider} 同一条装配条件
 * （{@code file-center.storage-type=minio}，缺省即 minio）：Bean 创建时会连 MinIO 并建桶，
 * 连不上直接抛异常。
 *
 * @author cxxl
 */
@Configuration
@ConditionalOnProperty(name = "file-center.storage-type", havingValue = "minio", matchIfMissing = true)
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(FileCenterConfig properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        ensureBucket(client, properties.getFileBucket());
        ensureBucket(client, properties.getArtifactBucket());
        return client;
    }

    /** 桶不存在时创建 */
    private void ensureBucket(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 桶初始化失败: " + bucket, e);
        }
    }
}
