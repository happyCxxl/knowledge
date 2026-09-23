package com.knowledge.vector;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Milvus 客户端工厂：统一生命周期（创建/关闭），业务层只取 Bean 使用。
 * 关闭开关（knowledge.vector.milvus.enable=false）时不注册，索引/检索环节跳过。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowledge.vector.milvus.enable", havingValue = "true", matchIfMissing = true)
public class MilvusClientFactory {

    private final MilvusProperties properties;

    private volatile MilvusClientV2 client;

    /**
     * 客户端 Bean（单例）。
     */
    @Bean
    public MilvusClientV2 milvusClientV2() {
        //noinspection HttpUrlsUsage
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + properties.getHost() + ":" + properties.getPort())
                .dbName(properties.getDatabase())
                .connectTimeoutMs(properties.getConnectTimeoutMs())
                .build();
        client = new MilvusClientV2(config);
        log.info("===> MilvusClientFactory Milvus 客户端已创建, uri={}, database={}",
                config.getUri(), config.getDbName());
        return client;
    }

    /**
     * 应用关闭时释放连接。
     */
    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Milvus 客户端关闭异常", e);
            }
        }
    }
}
