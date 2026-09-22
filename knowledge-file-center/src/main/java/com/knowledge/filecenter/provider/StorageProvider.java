package com.knowledge.filecenter.provider;

import java.io.InputStream;

/**
 * 存储提供者：原始对象的写入、读取与存在性（实现可替换）。
 *
 * @author cxxl
 */
public interface StorageProvider {

    /** 写入对象 */
    void put(String bucket, String key, InputStream in, long size, String contentType);

    /** 打开对象流 */
    InputStream get(String bucket, String key);

    /** 对象是否存在 */
    boolean exists(String bucket, String key);
}
