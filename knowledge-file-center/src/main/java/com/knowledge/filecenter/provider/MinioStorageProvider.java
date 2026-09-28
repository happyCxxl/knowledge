package com.knowledge.filecenter.provider;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 存储提供者：对象写入、读取与存在性。
 *
 * <p>装配开关见 {@code file-center.storage-type}：为 {@code local} 时本实现让位给
 * {@link LocalStorageProvider}。{@code matchIfMissing = true} 保证不配该键时仍走 MinIO
 * —— 与改造前行为一致，存量部署升级后无需改配置。
 *
 * @author cxxl
 */
@Service
@ConditionalOnProperty(name = "file-center.storage-type", havingValue = "minio", matchIfMissing = true)
public class MinioStorageProvider implements StorageProvider {

    private final MinioClient minioClient;

    public MinioStorageProvider(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void put(String bucket, String key, InputStream in, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 对象写入失败: " + key, e);
        }
    }

    @Override
    public InputStream get(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 对象读取失败: " + key, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
