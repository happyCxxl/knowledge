package com.knowledge.worker.structure.impl.craft;
import com.knowledge.common.enums.structure.UnifiedElementType;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.craft.MergeOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 去重合并单测：IoU+文本相似双判定 / 原生优先 / 同框不同内容冲突 PRIMARY-BACKUP。
 *
 * @author cxxl
 */
class DedupMergerImplTest {

    private DedupMergerImpl merger;
    private StructureProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StructureProperties();
        merger = new DedupMergerImpl();
    }

    private AssembleContext context() {
        AssembleContext context = new AssembleContext();
        context.setProperties(properties);
        return context;
    }

    private UnifiedElement element(String id, String text, BBox bbox, String source) {
        UnifiedElement element = new UnifiedElement();
        element.setId(id);
        element.setType(UnifiedElementType.PARAGRAPH.name());
        element.setText(text);
        element.setBbox(bbox);
        element.setExtension(Map.of("source", source));
        return element;
    }

    @Test
    void overlapSameTextShouldMergeNativeFirst() {
        BBox box = new BBox(72, 100, 300, 20);
        UnifiedElement nativeEl = element("n-1", "投标保证金为人民币叁佰万元整。", box, "native");
        UnifiedElement ocrEl = element("o-1", "投标保证金为人民币叁佰万元整。", new BBox(73, 101, 298, 19), "ocr");

        MergeOutcome outcome = merger.merge(List.of(nativeEl, ocrEl), context());

        assertEquals(1, outcome.getElements().size());
        assertEquals("n-1", outcome.getElements().getFirst().getId());
        assertEquals(1, outcome.getMergePairs());
        assertNull(nativeEl.getConflictStatus());
    }

    @Test
    void overlapDifferentTextShouldConflictWithPrimaryBackup() {
        BBox box = new BBox(72, 100, 300, 20);
        UnifiedElement nativeEl = element("n-1", "投标保证金为人民币叁佰万元整。", box, "native");
        UnifiedElement ocrEl = element("o-1", "担保函编号：GH-2024-8888", new BBox(73, 101, 298, 19), "ocr");

        MergeOutcome outcome = merger.merge(List.of(nativeEl, ocrEl), context());

        assertEquals(2, outcome.getElements().size());
        assertEquals(1, outcome.getConflicts().size());
        assertEquals("PRIMARY", nativeEl.getConflictStatus());
        assertEquals("BACKUP", ocrEl.getConflictStatus());
    }

    @Test
    void sameSourceShouldNotMerge() {
        BBox box = new BBox(72, 100, 300, 20);
        UnifiedElement a = element("n-1", "段落一内容", box, "native");
        UnifiedElement b = element("n-2", "段落一内容", new BBox(73, 101, 298, 19), "native");

        MergeOutcome outcome = merger.merge(List.of(a, b), context());

        assertEquals(2, outcome.getElements().size());
        assertEquals(0, outcome.getMergePairs());
    }
}
