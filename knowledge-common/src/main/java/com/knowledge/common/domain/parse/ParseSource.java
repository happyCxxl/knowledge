package com.knowledge.common.domain.parse;

import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.ParseSourceType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按来源分组的一路解析结果。各路不预先合并——保真与证据保留，合并裁决归组装环节。
 * 一期只有 native 路；ocr/layout/table 路随能力接入新增。
 *
 * @author cxxl
 */
@Data
public class ParseSource {

    /** 路名：native / ocr / layout / table */
    private String source;

    /** 具体解析器/提供方（如 pdfbox-3.0.4） */
    private String provider;

    /** 该路是否带候选顺序信息（组装环节阅读顺序参考） */
    private Boolean candidateOrder;

    /** 该路产出的元素列表 */
    private List<ParseElement> elements = new ArrayList<>();

    /** 页级原始指标（PDF 专用；判定器按阈值转信号） */
    private List<PageMetric> pageMetrics = new ArrayList<>();

    /** 解析期发现的事实信号（结构缺口/表格规则失败等；判定器汇总） */
    private List<ParseFact> facts = new ArrayList<>();

    /** 判定单元总数（PDF 页数 / Excel sheet 数 / Word 恒 1；由解析器回填） */
    private Integer unitCount;

    /** 页面尺寸（PDF 解析器回填；组装环节构建 UnifiedDocument.pages 的输入） */
    private List<PageDimension> pageDimensions = new ArrayList<>();

    /** 路级说明（如 ocr 路预留 note） */
    private String note;

    public static ParseSource nativeSource(String provider) {
        ParseSource source = new ParseSource();
        source.source = ParseSourceType.NATIVE.value();
        source.provider = provider;
        source.candidateOrder = true;
        return source;
    }
}
