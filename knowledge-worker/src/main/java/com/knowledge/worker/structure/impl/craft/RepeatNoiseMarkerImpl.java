package com.knowledge.worker.structure.impl.craft;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.domain.structure.UnifiedPage;
import com.knowledge.common.enums.structure.ElementMark;
import com.knowledge.common.enums.structure.PageMark;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.craft.MarkOutcome;
import com.knowledge.worker.structure.craft.RepeatNoiseMarker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 重复/噪声识别实现（字符 bigram Jaccard 相似度）：
 * 重复页——PDF 页文本聚合与前面页面比较 > repeatPageJaccard（除首份外标 REPEATED_PAGE）；
 * 重复段——文本元素（排除 HEADER/FOOTER）与前面元素比较 > repeatSegmentSimilarity
 * 且长度 ≥ repeatSegmentMinLen（除首份外标 REPEATED_SEGMENT）；
 * 噪声页——无元素页/仅图片页/乱码率 > noiseGarbledRatio（标 NOISE_PAGE）。
 * Word（无页概念）跳过页级、重复段照做；标记只增不改结构。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class RepeatNoiseMarkerImpl implements RepeatNoiseMarker {

    private final StructureProperties properties;

    @Override
    public MarkOutcome mark(UnifiedDocument document) {
        MarkOutcome outcome = new MarkOutcome();
        List<UnifiedElement> elements = ObjectUtil.defaultIfNull(document.getElements(), new ArrayList<>());

        markRepeatedPages(document, outcome);
        markRepeatedSegments(elements, outcome);
        markNoisePages(document, elements, outcome);
        return outcome;
    }

    // ---------------- 重复页（PDF，有页概念） ----------------

    private void markRepeatedPages(UnifiedDocument document, MarkOutcome outcome) {
        List<UnifiedPage> pages = document.getPages();
        if (ObjectUtil.isNull(pages) || pages.isEmpty()) {
            return;
        }
        Map<Integer, String> pageText = new HashMap<>();
        for (UnifiedElement element : document.getElements()) {
            if (ObjectUtil.isNotNull(element.getPage()) && StrUtil.isNotBlank(element.getText())) {
                pageText.merge(element.getPage(), element.getText(), String::concat);
            }
        }
        List<Integer> pageNumbers = pageText.keySet().stream().sorted().toList();
        List<Set<String>> shingles = new ArrayList<>();
        for (Integer pageNumber : pageNumbers) {
            shingles.add(TextUtil.bigramSet(pageText.get(pageNumber)));
        }
        for (int i = 0; i < pageNumbers.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (TextUtil.jaccardSet(shingles.get(i), shingles.get(j)) > properties.getRepeatPageJaccard()) {
                    addPageMark(pages, pageNumbers.get(i), PageMark.REPEATED_PAGE);
                    outcome.setRepeatPageCount(outcome.getRepeatPageCount() + 1);
                    break;
                }
            }
        }
    }

    // ---------------- 重复段 ----------------

    private void markRepeatedSegments(List<UnifiedElement> elements, MarkOutcome outcome) {
        List<ElementShingle> seen = new ArrayList<>();
        for (UnifiedElement element : elements) {
            if (!isTextElement(element) || StrUtil.isBlank(element.getText())
                    || element.getText().length() < properties.getRepeatSegmentMinLen()) {
                continue;
            }
            Set<String> current = TextUtil.bigramSet(element.getText());
            boolean repeated = false;
            for (ElementShingle earlier : seen) {
                if (TextUtil.jaccardSet(current, earlier.shingles) > properties.getRepeatSegmentSimilarity()) {
                    repeated = true;
                    break;
                }
            }
            if (repeated) {
                addElementMark(element, ElementMark.REPEATED_SEGMENT);
                outcome.setRepeatSegmentCount(outcome.getRepeatSegmentCount() + 1);
            } else {
                seen.add(new ElementShingle(element.getId(), current));
            }
        }
    }

    /** 文本元素：PARAGRAPH/TITLE（排除 HEADER/FOOTER——页眉页脚有独立处置路径） */
    private boolean isTextElement(UnifiedElement element) {
        String type = element.getType();
        return UnifiedElementType.PARAGRAPH.name().equals(type) || UnifiedElementType.TITLE.name().equals(type);
    }

    // ---------------- 噪声页（PDF） ----------------

    private void markNoisePages(UnifiedDocument document, List<UnifiedElement> elements, MarkOutcome outcome) {
        List<UnifiedPage> pages = document.getPages();
        if (ObjectUtil.isNull(pages) || pages.isEmpty()) {
            return;
        }
        Map<Integer, List<UnifiedElement>> byPage = new HashMap<>();
        for (UnifiedElement element : elements) {
            if (ObjectUtil.isNotNull(element.getPage())) {
                byPage.computeIfAbsent(element.getPage(), k -> new ArrayList<>()).add(element);
            }
        }
        for (UnifiedPage page : pages) {
            List<UnifiedElement> pageElements = byPage.getOrDefault(page.getPageNumber(), new ArrayList<>());
            boolean noise;
            String reason;
            if (pageElements.isEmpty()) {
                noise = true;
                reason = "空白页（无任何元素）";
            } else if (pageElements.stream().allMatch(e -> UnifiedElementType.IMAGE.name().equals(e.getType()))) {
                noise = true;
                reason = "纯图片占位页（无文字）";
            } else {
                String text = pageElements.stream()
                        .map(UnifiedElement::getText)
                        .filter(StrUtil::isNotBlank)
                        .reduce("", String::concat);
                noise = TextUtil.garbledRatio(text) > properties.getNoiseGarbledRatio();
                reason = "乱码率超阈值页";
            }
            if (noise) {
                addPageMark(pages, page.getPageNumber(), PageMark.NOISE_PAGE);
                outcome.setNoisePageCount(outcome.getNoisePageCount() + 1);
                outcome.getWarnings().add("页 " + page.getPageNumber() + "：" + reason);
            }
        }
    }

    // ---------------- 公共 ----------------

    private void addPageMark(List<UnifiedPage> pages, Integer pageNumber, PageMark mark) {
        for (UnifiedPage page : pages) {
            if (Objects.equals(page.getPageNumber(), pageNumber)) {
                if (page.getMarks() == null) {
                    page.setMarks(new ArrayList<>());
                }
                if (!page.getMarks().contains(mark.name())) {
                    page.getMarks().add(mark.name());
                }
                return;
            }
        }
    }

    private void addElementMark(UnifiedElement element, ElementMark mark) {
        if (element.getMarks() == null) {
            element.setMarks(new ArrayList<>());
        }
        if (!element.getMarks().contains(mark.name())) {
            element.getMarks().add(mark.name());
        }
    }

    private record ElementShingle(String elementId, Set<String> shingles) {
    }
}
