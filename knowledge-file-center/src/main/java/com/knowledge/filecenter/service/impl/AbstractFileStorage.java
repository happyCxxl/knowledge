package com.knowledge.filecenter.service.impl;

import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.config.FileCenterConfig;
import com.knowledge.filecenter.provider.StorageProvider;
import com.knowledge.filecenter.service.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 文件存储模板基类：sha256 内容寻址对象的写入/读取公共实现，并提供摘要写入骨架。
 *
 * @author cxxl
 */
public abstract class AbstractFileStorage implements FileStorage {

    protected final StorageProvider provider;

    protected final FileCenterConfig properties;

    protected AbstractFileStorage(StorageProvider provider, FileCenterConfig properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public String putObject(byte[] content) {
        String sha256 = sha256Hex(content);
        try {
            if (!provider.exists(properties.getArtifactBucket(), sha256)) {
                provider.put(properties.getArtifactBucket(), sha256,
                        new ByteArrayInputStream(content), content.length, "application/octet-stream");
            }
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "对象写入失败: " + sha256);
        }
        return sha256;
    }

    @Override
    public byte[] getObject(String sha256) {
        try (InputStream in = provider.get(properties.getArtifactBucket(), sha256)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "对象读取失败: " + sha256);
        }
    }

    @Override
    public boolean objectExists(String sha256) {
        return provider.exists(properties.getArtifactBucket(), sha256);
    }

    /** 摘要写入：流经 sha256 摘要后写入提供者，返回摘要十六进制。 */
    protected String putWithDigest(String bucket, String key, InputStream in, long size, String contentType) {
        try {
            DigestInputStream digest = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
            provider.put(bucket, key, digest, size, contentType);
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "对象写入失败: " + key);
        }
    }

    /** 字节内容摘要（sha256 十六进制）。 */
    protected String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "对象指纹计算失败");
        }
    }
}
