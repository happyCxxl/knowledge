package com.knowledge.common.dto.response.home;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页行为记录视图对象（数据源：kb_audit_log，只含管理员关键操作）。
 *
 * <p>两条展示口径：
 *
 * <ol>
 *   <li>**动作给中文**：`actionLabel` 由后端下发，前端不再维护 15 种动作的映射表（会漂移）；</li>
 *   <li>**对象给名字**：审计表只存 `object_id`，这里按对象类型反查名字
 *       （知识库名 / 策略名 / 用户名），否则页面只能显示一串雪花 ID。</li>
 * </ol>
 *
 * <p>文件提交行为不在审计表内（在 `kb_submit_log`），首页行为记录按需求只展示审计动作。
 *
 * @author cxxl
 */
@Data
public class HomeActivityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审计记录 ID */
    private Long id;

    /** 动作枚举名（如 PUBLISH_INDEX；供前端按类型着色） */
    private String actionType;

    /** 动作中文（如「发布索引」；后端下发，避免前端维护映射表） */
    private String actionLabel;

    /** 对象类型（KNOWLEDGE_BASE / INDEX_VERSION / KB_USER） */
    private String objectType;

    /** 对象类型中文（如「知识库」） */
    private String objectTypeLabel;

    /** 对象 ID（字符串下发） */
    private String objectId;

    /**
     * 对象名（按对象类型反查）。
     *
     * <p>索引版本特殊：审计只记版本号，这里回填为「{知识库名} {版本号}」，
     * 因为「发布索引 v3」不说库名等于没说。
     */
    private String objectName;

    /** 变更前的可读摘要（可空：创建类动作为空） */
    private String beforeSummary;

    /** 变更后的可读摘要（可空：删除类动作为空） */
    private String afterSummary;

    /** 操作人用户名（无认证上下文时为 system） */
    private String operator;

    /** 操作时间 */
    private LocalDateTime createTime;
}
