package com.knowledge.common.domain.parse.signal;

import lombok.Data;

/**
 * 解析期事实（解析器产出的原始发现，尚未按阈值判定）：
 * 结构缺口（OOXML drawing/OLE 仅图片）、表格规则失败区域、版面异常区域等。
 * SignalDetector 把事实 + 页级指标按阈值转成 Signal。
 *
 * @author cxxl
 */
@Data
public class ParseFact {

    /** 事实类型（与 SignalType 同名语义：OCR_TEXT/OCR_IMAGE/TABLE/LAYOUT） */
    private String type;

    /** 区域描述（页/元素引用） */
    private String region;

    /** 命中依据（如"第 3 页表格规则失败"） */
    private String evidence;
}
