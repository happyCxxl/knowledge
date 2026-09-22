package com.knowledge.common.dto.request.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建知识库请求。
 *
 * @author cxxl
 */
@Data
public class KnowledgeBaseCreateDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库名称（必填，≤128） */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称最长 128 字符")
    private String name;

    /** 业务场景说明（≤512） */
    @Size(max = 512, message = "业务场景说明最长 512 字符")
    private String description;

    /** 策略绑定开关：1 开启（默认）/ 0 关闭（测评模式，触发必须显式选策略）；null 视为开启 */
    private Integer strategyBindingEnabled;
}
