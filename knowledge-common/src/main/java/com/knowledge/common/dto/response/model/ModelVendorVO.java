package com.knowledge.common.dto.response.model;

import lombok.Data;

/**
 * 厂商下拉条目 VO：配置里列出的一个厂商。
 *
 * <p>只读清单，用于页面的厂商下拉；**不下发任何密钥内容**，只给出「该厂商是否已配密钥」这一项结论。
 *
 * @author cxxl
 */
@Data
public class ModelVendorVO {

    /** 厂商码（配置键 knowledge.model.vendors.<码> 里的码） */
    private String code;

    /** 厂商展示名（内置映射；码值认不出时回落到码值本身） */
    private String displayName;

    /** 是否已配密钥（读配置里的 api-key 是否有值，密钥内容不回显） */
    private boolean credentialConfigured;

    /** 配置里是否只有这一个厂商（页面据此决定下拉是否直接选中） */
    private boolean soleVendor;
}
