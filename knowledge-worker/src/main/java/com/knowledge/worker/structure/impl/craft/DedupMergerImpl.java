package com.knowledge.worker.structure.impl.craft;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.structure.ConflictRecord;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.parse.ParseSourceType;
import com.knowledge.common.enums.structure.ConflictStatus;
import com.knowledge.common.enums.structure.ElementExtensionKey;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.craft.DedupMerger;
import com.knowledge.worker.structure.craft.MergeOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 去重与合并实现：IoU + 文本相似（Jaccard）双判定缺一不可；原生优先裁决；
 * 同框不同内容（IoU 高、相似度低）→ 无法裁决 → PRIMARY/BACKUP 保留两路 + CONFLICT。
 * 一期 native 单路直通（无跨来源对）；框架完整，OCR/模型路接入即生效。
 *
 * @author cxxl
 */
@Component
public class DedupMergerImpl implements DedupMerger {

    /** 来源优先级：原生 > OCR/版面/表格模型 */
    private static final List<ParseSourceType> SOURCE_PRIORITY =
            List.of(ParseSourceType.NATIVE, ParseSourceType.OCR, ParseSourceType.LAYOUT, ParseSourceType.TABLE);

    @Override
    public MergeOutcome merge(List<UnifiedElement> elements, AssembleContext context) {
        MergeOutcome outcome = new MergeOutcome();
        outcome.setElements(new ArrayList<>(elements));
        double iouThreshold = context.getProperties().getIouThreshold();
        double textThreshold = context.getProperties().getTextSimilarityThreshold();

        Set<Integer> removed = new HashSet<>();
        for (int i = 0; i < elements.size(); i++) {
            if (removed.contains(i)) {
                continue;
            }
            for (int j = i + 1; j < elements.size(); j++) {
                if (removed.contains(j)) {
                    continue;
                }
                UnifiedElement a = elements.get(i);
                UnifiedElement b = elements.get(j);
                if (!isCrossSource(a, b) || !bothHaveBBox(a, b)) {
                    continue;
                }
                double iou = iou(a.getBbox(), b.getBbox());
                double similarity = TextUtil.jaccardCharSet(a.getText(), b.getText());
                if (iou > iouThreshold && similarity > textThreshold) {
                    // 同一个东西：原生优先，否则先到者保留；被弃证据可追溯（保留在冲突记录外，记合并数）
                    if (isPrior(a, b)) {
                        removed.add(j);
                    } else {
                        removed.add(i);
                        break;
                    }
                    outcome.setMergePairs(outcome.getMergePairs() + 1);
                } else if (iou > iouThreshold && similarity <= textThreshold) {
                    // 同框不同内容且无法裁决：两路都留，PRIMARY/BACKUP 标记 + CONFLICT
                    ConflictRecord record = new ConflictRecord();
                    if (isPrior(a, b)) {
                        a.setConflictStatus(ConflictStatus.PRIMARY.name());
                        b.setConflictStatus(ConflictStatus.BACKUP.name());
                        record.setPrimaryElementId(a.getId());
                        record.setBackupElementId(b.getId());
                    } else {
                        b.setConflictStatus(ConflictStatus.PRIMARY.name());
                        a.setConflictStatus(ConflictStatus.BACKUP.name());
                        record.setPrimaryElementId(b.getId());
                        record.setBackupElementId(a.getId());
                    }
                    record.setMessage("同框内容矛盾且无法裁决（IoU " + String.format("%.2f", iou)
                            + "，相似度 " + String.format("%.2f", similarity) + "）");
                    outcome.getConflicts().add(record);
                }
            }
        }
        List<UnifiedElement> kept = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (!removed.contains(i)) {
                kept.add(elements.get(i));
            }
        }
        outcome.setElements(kept);
        return outcome;
    }

    private boolean isCrossSource(UnifiedElement a, UnifiedElement b) {
        Object sourceA = a.getExtension() == null ? null : a.getExtension().get(ElementExtensionKey.SOURCE.key());
        Object sourceB = b.getExtension() == null ? null : b.getExtension().get(ElementExtensionKey.SOURCE.key());
        return ObjectUtil.isNotNull(sourceA) && ObjectUtil.isNotNull(sourceB) && !sourceA.equals(sourceB);
    }

    private boolean bothHaveBBox(UnifiedElement a, UnifiedElement b) {
        return ObjectUtil.isNotNull(a.getBbox()) && ObjectUtil.isNotNull(b.getBbox());
    }

    private boolean isPrior(UnifiedElement a, UnifiedElement b) {
        Object rawA = a.getExtension() == null ? null : a.getExtension().get(ElementExtensionKey.SOURCE.key());
        Object rawB = b.getExtension() == null ? null : b.getExtension().get(ElementExtensionKey.SOURCE.key());
        ParseSourceType sourceA = ObjectUtil.isNull(rawA) ? null : ParseSourceType.ofValue(String.valueOf(rawA));
        ParseSourceType sourceB = ObjectUtil.isNull(rawB) ? null : ParseSourceType.ofValue(String.valueOf(rawB));
        return SOURCE_PRIORITY.indexOf(sourceA) <= SOURCE_PRIORITY.indexOf(sourceB);
    }

    private double iou(BBox a, BBox b) {
        double x1 = Math.max(a.getX(), b.getX());
        double y1 = Math.max(a.getY(), b.getY());
        double x2 = Math.min(a.getX() + a.getWidth(), b.getX() + b.getWidth());
        double y2 = Math.min(a.getY() + a.getHeight(), b.getY() + b.getHeight());
        double intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double areaA = a.getWidth() * a.getHeight();
        double areaB = b.getWidth() * b.getHeight();
        double union = areaA + areaB - intersection;
        return union > 0 ? intersection / union : 0;
    }
}
