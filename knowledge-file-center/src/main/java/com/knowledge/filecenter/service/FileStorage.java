package com.knowledge.filecenter.service;

import com.knowledge.common.domain.input.FileMetadata;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储：文件对象的写入与读取（实现由调用方选定，提供者可替换）。
 *
 * <p>寻址方式两种：fileId 寻址（每次写入产生新对象，元数据入档案库）
 * 与 sha256 内容寻址（同内容幂等复用，无档案）。
 *
 * @author cxxl
 */
public interface FileStorage {

    // —— fileId 寻址：每次写入新对象，元数据入档案库 ——

    /** 写入文件对象，返回 fileId */
    String putFile(MultipartFile file);

    /** 打开文件流 */
    InputStream open(String fileId);

    /** 读取文件元数据 */
    FileMetadata metadata(String fileId);

    /** 文件是否存在 */
    boolean exists(String fileId);

    // —— sha256 内容寻址：同内容幂等复用，无档案 ——

    /** 写入对象，返回 sha256 */
    String putObject(byte[] content);

    /** 读取对象 */
    byte[] getObject(String sha256);

    /** 对象是否存在 */
    boolean objectExists(String sha256);
}
