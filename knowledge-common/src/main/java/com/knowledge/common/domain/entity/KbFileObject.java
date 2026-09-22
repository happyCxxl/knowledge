package com.knowledge.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowledge.infra.domain.base.BaseCreateInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件档案：上传文件的元数据记录（append-only）。
 *
 * @author cxxl
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_file_object")
public class KbFileObject extends BaseCreateInfo {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文件 ID（雪花字符串，同时是 MinIO 对象键） */
    private String fileId;

    /** 文件名 */
    private String fileName;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容指纹（sha256） */
    private String sha256;

    /** 存储桶名 */
    private String bucket;

    /** 对象键 */
    private String objectKey;
}
