package com.knowledge.worker.structure.impl.craft;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.parse.ParseSourceType;
import com.knowledge.common.enums.structure.ElementExtensionKey;
import com.knowledge.common.enums.structure.ElementMark;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.craft.ElementNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元素标准化实现：ParseElement → UnifiedElement 雏形（类型映射 + 全局 ID + 扩展区透传）。
 * 坐标口径由解析环节统一为左上角原点 + pt，本期直通；像素×72÷DPI 路径随 OCR 接入补充。
 *
 * @author cxxl
 */
@Component
public class NormalizerImpl implements ElementNormalizer {

    @Override
    public List<UnifiedElement> normalize(List<ParseSource> sources, AssembleContext context) {
        List<UnifiedElement> result = new ArrayList<>();
        if (ObjectUtil.isNull(sources)) {
            return result;
        }
        for (ParseSource source : sources) {
            for (ParseElement element : source.getElements()) {
                UnifiedElement unified = toUnified(element, source.getProvider());
                if (ObjectUtil.isNotNull(unified)) {
                    result.add(unified);
                }
            }
        }
        return result;
    }

    private UnifiedElement toUnified(ParseElement element, String provider) {
        UnifiedElementType type = mapType(element.getType());
        if (ObjectUtil.isNull(type)) {
            return null;
        }
        UnifiedElement unified = new UnifiedElement();
        unified.setType(type.name());
        unified.setText(element.getText());
        unified.setAssetRef(element.getAssetRef());
        unified.setPage(element.getPage());
        unified.setBbox(element.getBbox());
        unified.setFont(element.getFont());
        unified.setRows(element.getRows());
        unified.setCols(element.getCols());
        unified.setHeaderRow(element.getHeaderRow());
        // 单元格行列/合并/表头标记透传（切片环节表头随片与行组依赖）
        unified.setRow(element.getRow());
        unified.setCol(element.getCol());
        unified.setRowSpan(element.getRowSpan());
        unified.setColSpan(element.getColSpan());
        unified.setIsHeader(element.getIsHeader());
        unified.setCaption(null);

        // 单元格嵌套（TABLE 的子元素）
        if (ObjectUtil.isNotNull(element.getCells())) {
            List<UnifiedElement> cells = new ArrayList<>();
            for (ParseElement cell : element.getCells()) {
                UnifiedElement cellUnified = toUnified(cell, provider);
                if (ObjectUtil.isNotNull(cellUnified)) {
                    cells.add(cellUnified);
                }
            }
            unified.setCells(cells);
        }

        // 扩展区：解析器特有字段透传（下游只依赖公共字段）
        Map<String, Object> extension = new HashMap<>();
        extension.put(ElementExtensionKey.SOURCE.key(), StrUtil.blankToDefault(provider, ParseSourceType.NATIVE.value()));
        if (StrUtil.isNotBlank(element.getStyle())) {
            extension.put(ElementExtensionKey.STYLE.key(), element.getStyle());
        }
        if (StrUtil.isNotBlank(element.getSheetName())) {
            extension.put(ElementExtensionKey.SHEET_NAME.key(), element.getSheetName());
        }
        if (ObjectUtil.isNotNull(element.getNeedsOcr())) {
            extension.put(ElementExtensionKey.NEEDS_OCR.key(), element.getNeedsOcr());
        }
        if (ObjectUtil.isNotNull(element.getCutAtPageBottom())) {
            extension.put(ElementExtensionKey.CUT_AT_PAGE_BOTTOM.key(), element.getCutAtPageBottom());
        }
        if (ObjectUtil.isNotNull(element.getHeaderRepeated())) {
            extension.put(ElementExtensionKey.HEADER_REPEATED.key(), element.getHeaderRepeated());
        }
        unified.setExtension(extension);
        unified.setProvenance(element.getProvenance());

        // 目录行级特征透传（解析环节识别 → 预处理环节处置；整页聚合判定在预处理环节）
        if (Boolean.TRUE.equals(element.getTocCandidate())) {
            unified.setMarks(new java.util.ArrayList<>(List.of(ElementMark.TOC_LINE.name())));
        }

        // 全局 ID = 前缀 + 内容哈希（原文 id + 文本 + 坐标 + 来源，保证单文档内唯一）
        String seed = provider + "|" + element.getId() + "|" + StrUtil.blankToDefault(element.getText(), "")
                + "|" + (ObjectUtil.isNull(element.getBbox()) ? "" : element.getBbox().toString());
        unified.setId(StructureIds.of(type, seed));
        return unified;
    }

    private UnifiedElementType mapType(String parseType) {
        if (StrUtil.isBlank(parseType)) {
            return null;
        }
        try {
            return UnifiedElementType.valueOf(parseType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
