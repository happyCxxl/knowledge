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
import com.knowledge.common.utils.NullUtil;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Set<String> relationKeys = new HashSet<>();
        List<UnifiedElement> workElements = outcome.getElements();
        if (isExcel(context.getSourceFileType())) {
            appendRelations(relations, relationKeys, buildSheetSections(workElements));
        }

        // ③ 章节树（PARENT_CHILD）
        appendRelations(relations, relationKeys, buildChapterTree(workElements));

        // ④ 阅读顺序关系（NEXT/PREVIOUS，页眉页脚排除正文流）
        appendRelations(relations, relationKeys, buildOrderRelations(workElements));

        // ⑤ 单元格归属（TABLE_CELL_OF）
        appendRelations(relations, relationKeys, buildCellRelations(workElements));

        outcome.setRelations(relations);
        return outcome;
    }

    /** 关系累积：同一（类型、起点、终点）只保留首个（sheet 归属与章节树可能给出同一对父子） */
    private void appendRelations(List<DocumentRelation> target, Set<String> keys,
                                 List<DocumentRelation> additions) {
        for (DocumentRelation relation : additions) {
            String key = relation.getType() + "|" + relation.getFrom() + "|" + relation.getTo();
            if (keys.add(key)) {
                target.add(relation);
            }
        }
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
            if (NullUtil.isNotNull(decision)) {
                return decision;
            }
        }
        // 模型兜底：规则拿不准 → 开关开且有实现才调用
        var judge = judgeRegistry.active();
        if (NullUtil.isNotNull(judge) && ruleContext.shortText()) {
            TitleJudgeContext judgeContext = new TitleJudgeContext();
            judgeContext.setCandidateText(ruleContext.text());
            judgeContext.setFontSize(ruleContext.fontSize());
            judgeContext.setBold(ruleContext.element().getFont() == null
                    ? null : ruleContext.element().getFont().getBold());
            judgeContext.setNumberingPattern(null);
            TitleJudgeResult result = judge.judgeTitle(judgeContext);
            if (NullUtil.isNotNull(result) && Boolean.TRUE.equals(result.getIsTitle())) {
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
                .map(e -> NullUtil.isNull(e.getFont()) ? null : e.getFont().getSize())
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
        if (NullUtil.isNull(element.getExtension())) {
            return null;
        }
        Object value = element.getExtension().get(key);
        return NullUtil.isNull(value) ? null : String.valueOf(value);
    }

    // ---------------- 章节树 / 归属 / 顺序 ----------------

    private List<DocumentRelation> buildSheetSections(List<UnifiedElement> elements) {
        // 按 sheet 名分组（保持出现顺序），并记录各 sheet 首个元素的下标
        Map<String, List<UnifiedElement>> sheetGroups = new LinkedHashMap<>();
        Map<String, Integer> firstIndexes = new LinkedHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            UnifiedElement element = elements.get(i);
            if (!UnifiedElementType.TABLE.name().equals(element.getType())) {
                continue;
            }
            String sheetName = extensionString(element, ElementExtensionKey.SHEET_NAME.key());
            if (StrUtil.isBlank(sheetName)) {
                continue;
            }
            sheetGroups.computeIfAbsent(sheetName, k -> new ArrayList<>()).add(element);
            firstIndexes.putIfAbsent(sheetName, i);
        }
        List<String> sheetNames = new ArrayList<>(sheetGroups.keySet());
        if (sheetNames.isEmpty()) {
            return new ArrayList<>();
        }
        List<UnifiedElement> sections = new ArrayList<>();
        for (String sheetName : sheetNames) {
            sections.add(sheetSection(sheetName));
        }
        // 自后向前插入：章节紧邻各自 sheet 的首个元素，顺序即 章节1、表1、章节2、表2 …
        for (int i = sheetNames.size() - 1; i >= 0; i--) {
            elements.add(firstIndexes.get(sheetNames.get(i)), sections.get(i));
        }
        List<DocumentRelation> relations = new ArrayList<>();
        for (int i = 0; i < sheetNames.size(); i++) {
            for (UnifiedElement table : sheetGroups.get(sheetNames.get(i))) {
                relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                        sections.get(i).getId(), table.getId(), "sheet 归属"));
            }
        }
        return relations;
    }

    /** sheet 章节节点：id 由 sheet 名派生，层级固定 0（工作表是顶层章节） */
    private UnifiedElement sheetSection(String sheetName) {
        UnifiedElement section = new UnifiedElement();
        section.setId(StructureIds.of(UnifiedElementType.SECTION, "sheet|" + sheetName));
        section.setType(UnifiedElementType.SECTION.name());
        section.setText(sheetName);
        section.setLevel(0);
        return section;
    }

    private List<DocumentRelation> buildChapterTree(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        List<Integer> stackLevels = new ArrayList<>();
        for (UnifiedElement element : elements) {
            String type = element.getType();
            if (UnifiedElementType.TITLE.name().equals(type)) {
                int level = ObjectUtil.defaultIfNull(element.getLevel(), 1);
                popSameOrDeeper(stack, stackLevels, level);
                addParentChild(relations, stack, element, null);
                push(stack, stackLevels, element.getId(), level);
            } else if (UnifiedElementType.SECTION.name().equals(type)) {
                popSameOrDeeper(stack, stackLevels, 0); // 同级章节互为兄弟
                addParentChild(relations, stack, element, "sheet 章节");
                push(stack, stackLevels, element.getId(), 0);
            } else if (!UnifiedElementType.HEADER.name().equals(type)
                    && !UnifiedElementType.FOOTER.name().equals(type)) {
                addParentChild(relations, stack, element, null);
            }
        }
        return relations;
    }

    /** 弹出层级不浅于当前层级的节点（同级或更深者出栈，保证同级互为兄弟） */
    private void popSameOrDeeper(List<String> stack, List<Integer> stackLevels, int level) {
        while (!stackLevels.isEmpty() && stackLevels.getLast() >= level) {
            stackLevels.removeLast();
            stack.removeLast();
        }
    }

    /** 当前节点入栈（正文元素不入栈，只挂到栈顶章节） */
    private void push(List<String> stack, List<Integer> stackLevels, String elementId, int level) {
        stack.add(elementId);
        stackLevels.add(level);
    }

    /** 挂到栈顶章节（栈空即顶级，无父关系）；note 为关系说明（无则空） */
    private void addParentChild(List<DocumentRelation> relations, List<String> stack, UnifiedElement element,
                                String note) {
        if (!stack.isEmpty()) {
            relations.add(new DocumentRelation(RelationType.PARENT_CHILD.name(),
                    stack.getLast(), element.getId(), note));
        }
    }

    private List<DocumentRelation> buildOrderRelations(List<UnifiedElement> elements) {
        List<DocumentRelation> relations = new ArrayList<>();
        UnifiedElement previous = null;
        for (UnifiedElement element : elements) {
            String type = element.getType();
            if (UnifiedElementType.HEADER.name().equals(type) || UnifiedElementType.FOOTER.name().equals(type)) {
                continue; // 页眉页脚排除正文流（仍在树内）
            }
            if (NullUtil.isNotNull(previous)) {
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
                    && NullUtil.isNotNull(element.getCells())) {
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
