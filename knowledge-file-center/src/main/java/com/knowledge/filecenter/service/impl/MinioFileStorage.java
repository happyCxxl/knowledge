package com.knowledge.filecenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.knowledge.common.domain.entity.KbFileObject;
import com.knowledge.common.domain.input.FileMetadata;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.config.FileCenterConfig;
import com.knowledge.filecenter.db.KbFileObjectDbService;
import com.knowledge.filecenter.provider.StorageProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储实现：fileId 寻址（对象写入 MinIO，元数据写入 kb_file_object 档案表）；
 * sha256 内容寻址对象读写继承自 {@link AbstractFileStorage}。
 *
 * @author cxxl
 */
@Service
public class MinioFileStorage extends AbstractFileStorage {

    private final KbFileObjectDbService fileObjectDbService;

    public MinioFileStorage(StorageProvider provider, FileCenterConfig properties,
                            KbFileObjectDbService fileObjectDbService) {
        super(provider, properties);
        this.fileObjectDbService = fileObjectDbService;
    }

    @Override
    public String putFile(MultipartFile file) {
        String fileId = IdWorker.getIdStr();
        String sha256;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            sha256 = putWithDigest(properties.getFileBucket(), fileId, in, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + file.getOriginalFilename());
        }

        KbFileObject object = new KbFileObject();
        object.setFileId(fileId);
        object.setFileName(file.getOriginalFilename());
        object.setMimeType(file.getContentType());
        object.setFileSize(file.getSize());
        object.setSha256(sha256);
        object.setBucket(properties.getFileBucket());
        object.setObjectKey(fileId);
        fileObjectDbService.save(object);
        return fileId;
    }

    @Override
    public InputStream open(String fileId) {
        KbFileObject object = requireObject(fileId);
        try {
            return provider.get(object.getBucket(), object.getObjectKey());
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR, "文件读取失败: " + fileId);
        }
    }

    @Override
    public FileMetadata metadata(String fileId) {
        KbFileObject object = requireObject(fileId);
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(object.getFileId());
        metadata.setFileName(object.getFileName());
        metadata.setFileSize(object.getFileSize());
        metadata.setMimeType(object.getMimeType());
        metadata.setSha256(object.getSha256());
        metadata.setCreateTime(object.getCreateTime());
        return metadata;
    }

    @Override
    public boolean exists(String fileId) {
        return fileObjectDbService.getByFileId(fileId) != null;
    }

    /** 取档案，不存在抛业务错误 */
    private KbFileObject requireObject(String fileId) {
        KbFileObject object = fileObjectDbService.getByFileId(fileId);
        if (object == null) {
            throw new KnowledgeException(ErrorCode.FILE_NOT_FOUND);
        }
        return object;
    }
}
