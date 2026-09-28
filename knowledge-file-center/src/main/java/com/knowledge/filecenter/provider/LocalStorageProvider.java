package com.knowledge.filecenter.provider;

import com.knowledge.filecenter.config.FileCenterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 本地磁盘存储提供者：把 {@code bucket} 当子目录、{@code key} 当文件名落到本地目录。
 *
 * <p>**为什么 bucket 能直接复用为目录名**：{@code kb_file_object.bucket} 本来就把桶名记在档
 * 案里（MinIO 实现写的是 {@code properties.getFileBucket()}），读取时也从档案里取。
 * 所以换成磁盘目录后 **DB 契约一个字都不用改**，只是"桶"变成了目录层级。
 *
 * <p>**目录布局**：{@code {local-root}/{bucket}/{key}}，与对象存储的两级结构一一对应，
 * 便于人工比对与迁移。
 *
 * <p>**路径逃逸防护**：{@code key} 来自调用方（fileId 或 sha256），虽然当前调用方不会传
 * {@code ..}，但提供者是通用层，一旦上游有拼接缺陷就会写到仓库外。这里统一做规范化后
 * 校验前缀，把风险挡在存储层。
 *
 * <p>**不是原子的**：写入用临时文件 + 移动实现"内容不半截"，但不做 fsync、不加锁。
 * 本实现定位是**开发/演示用的本地替代**，并发与断电语义不承诺强于对象存储。
 *
 * @author cxxl
 */
@Service
@ConditionalOnProperty(name = "file-center.storage-type", havingValue = "local")
public class LocalStorageProvider implements StorageProvider {

    /** 临时文件后缀：先写这个再原子移动到目标名，避免读到写了一半的对象 */
    private static final String TMP_SUFFIX = ".part";

    private final Path root;

    public LocalStorageProvider(FileCenterConfig properties) {
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String bucket, String key, InputStream in, long size, String contentType) {
        Path target = resolve(bucket, key);
        Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        try {
            // 只建父目录：createDirectories(target) 会把 target 本身建成目录，随后 copy 必然失败
            Files.createDirectories(parentOf(target));
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new IllegalStateException("本地对象写入失败: " + bucket + "/" + key, e);
        }
    }

    @Override
    public InputStream get(String bucket, String key) {
        Path target = resolve(bucket, key);
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("本地对象不存在: " + bucket + "/" + key);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("本地对象读取失败: " + bucket + "/" + key, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return Files.isRegularFile(resolve(bucket, key));
    }

    /**
     * 解析对象路径并校验没有逃出根目录。
     *
     * @param bucket 桶名（本地实现里是根目录下的一级子目录）
     * @param key    对象名（fileId 或 sha256）
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 路径为空或越出根目录
     */
    /**
     * 解析对象路径并校验没有逃出根目录。
     *
     * <p>**前置校验而不是事后校验**：{@code ..} 与绝对路径在拼进 root 之前就拒掉，
     * 这样"两者都能进入拼接"时的最坏情况（例如平台相关的路径语义差异）根本不会发生。
     * 事后再用 {@code startsWith} 兜一道。
     *
     * @param bucket 桶名（本地实现里是根目录下的一级子目录）
     * @param key    对象名（fileId 或 sha256）
     * @return 规范化后的绝对路径（**必然含有父目录**，因为它至少包含 bucket 与 key 两段）
     * @throws IllegalArgumentException 路径为空、含越级片段或绝对路径
     */
    private Path resolve(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("bucket 与 key 均不可为空");
        }
        if (containsTraversal(bucket) || containsTraversal(key)) {
            throw new IllegalArgumentException("对象路径含越级片段: " + bucket + "/" + key);
        }
        Path candidate = root.resolve(bucket).resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("对象路径越出存储根目录: " + bucket + "/" + key);
        }
        return candidate;
    }

    /** 是否含 {@code ..} 片段或以路径分隔符开头（后者会让 resolve 丢弃 root） */
    private boolean containsTraversal(String segment) {
        if (segment.startsWith("/") || segment.startsWith("\\")) {
            return true;
        }
        for (String part : segment.split("[/\\\\]")) {
            if ("..".equals(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取对象路径的父目录，并显式处理 null。
     *
     * <p>{@link #resolve} 保证路径至少有 bucket 与 key 两段，所以父目录必然存在；
     * 这里显式判空是为了让静态分析看得懂，同时把"万一为空"变成明确的错误而不是 NPE。
     */
    private Path parentOf(Path target) {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("对象路径缺少父目录: " + target);
        }
        return parent;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程；残留的 .part 文件不会被 exists/get 命中
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalStorageProvider provider && Objects.equals(root, provider.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root);
    }
}
