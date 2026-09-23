package com.knowledge.common.dto.response.knowledge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库视图对象（status 返回码值 1/0）。
 *
 * @author cxxl
 */
@Data
public class KnowledgeBaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花 ID，前端按字符串处理防精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    /** 状态码值：1 启用 / 0 停用（见 KnowledgeBaseStatus） */
    private Integer status;

    /** 策略绑定开关：1 开启 / 0 关闭（测评模式，触发必须显式选策略） */
    private Integer strategyBindingEnabled;

    /** 绑定的切片策略版本行 ID（无绑定为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chunkStrategyVersionId;

    /** 绑定的切片策略版本串（如 chunk-hybrid-v1，无绑定为 null） */
    private String chunkStrategyVersion;

    /** 绑定的预处理策略版本行 ID（无绑定为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preprocessStrategyVersionId;

    /** 绑定的预处理策略版本串（如 preproc-default-v1，无绑定为 null） */
    private String preprocessStrategyVersion;

    /** 默认知识库标记：1=默认库（全库唯一，固定不可停用/删除）/ 0=普通库 */
    private Integer defaultFlag;

    /** 创建用户ID（无认证上下文为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
