package com.knowledge.worker.structure.impl.craft;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.structure.DocumentRelation;
import com.knowledge.common.domain.structure.TitleEvidence;
import com.knowledge.common.domain.structure.TitleJudgeContext;
import com.knowledge.common.domain.structure.TitleJudgeResult;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.enums.structure.ElementExtensionKey;
import com.knowledge.common.enums.structure.RelationType;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.craft.StructureAssembler;
import com.knowledge.worker.structure.craft.TreeOutcome;
import com.knowledge.worker.structure.impl.StructureJudgeRegistry;
import com.knowledge.worker.structure.title.TitleDecision;
import com.knowledge.worker.structure.title.TitleRule;
import com.knowledge.worker.structure.title.TitleRuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构组装实现：标题判定规则链（样式→章级→多级数字→中文括号→中文顿号→字号加粗，按 order 依次尝试）+ 模型兜底
 * （规则全 miss 且开关开才调用）+ 章节树 PARENT_CHILD + NEXT/PREVIOUS + TABLE_CELL_OF + Excel sheet→SECTION。
 * "拿不准" → 固定规则降级（按正文）+ 标题候选告警；模型兜底在规则之后、告警之前。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class StructureAssemblerImpl implements StructureAssembler {

    private final StructureJudgeRegistry judgeRegistry;
    private final List<TitleRule> titleRules;

    @Override
    public TreeOutcome assembleTree(List<UnifiedElement> ordered, AssembleContext context) {
        TreeOutcome outcome = new TreeOutcome();
        outcome.setElements(new ArrayList<>(ordered));
        Map<String, Integer> cascadeCounts = new HashMap<>();

        // 文档字号基准（字号/加粗启发式规则用）
        double medianSize = medianFontSize(ordered);

        // ① 标题推定（规则链依次尝试，首个命中即止；模型兜底在链尾）
        List<TitleRule> rules = titleRules.stream()
                .sorted(Comparator.comparingInt(TitleRule::order))
                .toList();
        for (UnifiedElement element : ordered) {
            if (!UnifiedElementType.PARAGRAPH.name().equals(element.getType())) {
                continue;
            }
            TitleDecision decision = decideTitle(element, medianSize, context.getProperties(), rules);
            if (decision.isTitle()) {
                element.setType(UnifiedElementType.TITLE.name());
                element.setLevel(decision.getLevel());
                element.setTitleEvidence(decision.getEvidence());
                outcome.setTitleCount(outcome.getTitleCount() + 1);
                cascadeCounts.merge(decision.getEvidence().getCascade(), 1, Integer::sum);
            } else if (decision.isCandidate()) {
                outcome.setTitleCandidateCount(outcome.getTitleCandidateCount() + 1);
            }
        }
        outcome.setTitleCountByCascade(cascadeCounts);

        // ② Excel：sheet → SECTION
        List<DocumentRelation> relations = new ArrayList<>();
        List<UnifiedElement> workElements = outcome.getElements();
        if (isExcel(context.getSourceFileType())) {
            relations.addAll(buildSheetSections(workElements));
        }

        // ③ 章节树（PARENT_CHILD）
        relations.addAll(buildChapterTree(workElements));

        // ④ 阅读顺序关系（NEXT/PREVIOUS，页眉页脚排除正文流）
        relations.addAll(buildOrderRelations(workElements));

        // ⑤ 单元格归属（TABLE_CELL_OF）
        relations.addAll(buildCellRelations(workElements));

        outcome.setRelations(relations);
        return outcome;
    }

    // ---------------- 标题判定（规则链 + 模型兜底） ----------------

    /** 规则链依次尝试（首个非空判定即止）；全 miss → 模型兜底（开关开且有实现）→ 非标题。 */
    private TitleDecision decideTitle(UnifiedElement element, double medianSize,
                                      StructureProperties properties, List<TitleRule> rules) {
        TitleRuleContext ruleContext = new TitleRuleContext(element, medianSize, properties);
        return decideByRules(rules, ruleContext);
    }

    private TitleDecision decideByRules(List<TitleRule> rules, TitleRuleContext ruleContext) {
        for (TitleRule rule : rules) {
            TitleDecision decision = rule.tryMatch(ruleContext);
            if (ObjectUtil.isNotNull(decision)) {
                return decision;
            }
        }
        // 模型兜底：规则拿不准 → 开关开且有实现才调用
        var judge = judgeRegistry.active();
        if (ObjectUtil.isNotNull(judge) && ruleContext.shortText()) {
            TitleJudgeContext judgeContext = new TitleJudgeContext();
            judgeContext.setCandidateText(ruleContext.text());
            judgeContext.setFontSize(ruleContext.fontSize());
            judgeContext.setBold(ruleContext.element().getFont() == null
                    ? null : ruleContext.element().getFont().getBold());
            judgeContext.setNumberingPattern(null);
            TitleJudgeResult result = judge.judgeTitle(judgeContext);
            if (ObjectUtil.isNotNull(result) && Boolean.TRUE.equals(result.getIsTitle())) {
                TitleEvidence evidence = TitleDecision.evidence("model", ruleContext.fontSize(),
                        ruleContext.bold(), null);
                evidence.setModelEvidence(result.getModel() + "@" + result.getVersion()
                        + " conf=" + result.getConfidence());
                return TitleDecision.title(ObjectUtil.defaultIfNull(result.getLevel(), 2), evidence);
            }
        }
        return TitleDecision.paragraph();
    }

    private double medianFontSize(List<UnifiedElement> elements) {
        List<Double> sizes = elements.stream()
                .filter(e -> UnifiedElementType.PARAGRAPH.name().equals(e.getType()))
                .map(e -> ObjectUtil.isNull(e.getFont()) ? null : e.getFont().getSize())
                .filter(ObjectUtil::isNotNull)
                .sorted()
                .toList();
        if (sizes.isEmpty()) {
            return 0;
        }
        int mid = sizes.size() / 2;
        return sizes.size() % 2 == 1 ? sizes.get(mid) : (sizes.get(mid - 1) + sizes.get(mid)) / 2;
    }

    private String extensionString(UnifiedElement element, String key) {
        if (ObjectUtil.isNull(element.getExtension())) {
            return null;
        }
        Object value = element.getExtension().get(key);
        return ObjectUtil.isNull(value) ? null : String.valueOf(value);
    }

    // ---------------- 章节树 / 归属 / 顺序 ----------------

    private List<DocumentRelation> buildSheetSections(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        // 按 sheet 名分组（保持出现顺序）
        Map<String, List<UnifiedElement>> sheetGroups = new LinkedHashMap<>();
        for (UnifiedElement element : elements) {
            if (UnifiedElementType.TABLE.name().equals(element.getType())) {
                String sheetName = extensionString(element, ElementExtensionKey.SHEET_NAME.key());
                if (StrUtil.isNotBlank(sheetName)) {
                    sheetGroups.computeIfAbsent(sheetName, k -> new ArrayList<>()).add(element);
                }
            }
        }
        List<UnifiedElement> sections = new ArrayList<>();
        for (Map.Entry<String, List<UnifiedElement>> entry : sheetGroups.entrySet()) {
            UnifiedElement section = new UnifiedElement();
            section.setId(StructureIds.of(UnifiedElementType.SECTION, "sheet|" + entry.getKey()));
            section.setType(UnifiedElementType.SECTION.name());
            section.setText(entry.getKey());
            section.setLevel(0);
            sections.add(section);
            for (UnifiedElement table : entry.getValue()) {
                relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                        section.getId(), table.getId(), "sheet 归属"));
            }
        }
        // 章节节点统一前插（保持 sheet 出现顺序）
        elements.addAll(0, sections);
        return relations;
    }

    private List<DocumentRelation> buildChapterTree(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        List<Integer> stackLevels = new ArrayList<>();
        for (UnifiedElement element : elements) {
            String type = element.getType();
            if (UnifiedElementType.TITLE.name().equals(type)) {
                int level = ObjectUtil.defaultIfNull(element.getLevel(), 1);
                while (!stackLevels.isEmpty() && stackLevels.getLast() >= level) {
                    stackLevels.removeLast();
                    stack.removeLast();
                }
                if (!stack.isEmpty()) {
                    relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                            stack.getLast(), element.getId(), null));
                }
                stack.add(element.getId());
                stackLevels.add(level);
            } else if (UnifiedElementType.SECTION.name().equals(type)) {
                if (!stack.isEmpty()) {
                    relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                            stack.getLast(), element.getId(), "sheet 章节"));
                }
                stack.add(element.getId());
                stackLevels.add(0);
            } else if (!UnifiedElementType.HEADER.name().equals(type)
                    && !UnifiedElementType.FOOTER.name().equals(type)) {
                if (!stack.isEmpty()) {
                    relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                            stack.getLast(), element.getId(), null));
                }
            }
        }
        return relations;
    }

    private List<DocumentRelation> buildOrderRelations(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        UnifiedElement previous = null;
        for (UnifiedElement element : elements) {
            String type = element.getType();
            if (UnifiedElementType.HEADER.name().equals(type) || UnifiedElementType.FOOTER.name().equals(type)) {
                continue; // 页眉页脚排除正文流（仍在树内）
            }
            if (ObjectUtil.isNotNull(previous)) {
                relations.add(new DocumentRelation(RelationType.NEXT.name(), previous.getId(), element.getId(), null));
                relations.add(new DocumentRelation(RelationType.PREVIOUS.name(), element.getId(), previous.getId(), null));
            }
            previous = element;
        }
        return relations;
    }

    private List<DocumentRelation> buildCellRelations(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        for (UnifiedElement element : elements) {
            if (UnifiedElementType.TABLE.name().equals(element.getType())
                    && ObjectUtil.isNotNull(element.getCells())) {
                for (UnifiedElement cell : element.getCells()) {
                    relations.add(new DocumentRelation(RelationType.TABLE_CELL_OF.name(),
                            cell.getId(), element.getId(), null));
                }
            }
        }
        return relations;
    }

    private boolean isExcel(String mimeType) {
        return FileFormat.XLS.getMimeType().equals(mimeType)
                || FileFormat.XLSX.getMimeType().equals(mimeType);
    }
}
