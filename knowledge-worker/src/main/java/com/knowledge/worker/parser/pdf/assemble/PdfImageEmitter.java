package com.knowledge.worker.parser.pdf.assemble;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.detect.RuleLines;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.Region;

import java.util.Comparator;
import java.util.List;

/**
 * 页内图片元素与事实：面积占比达门槛的图片产 IMAGE 元素（仅引用 + needsOcr + bbox 定位），
 * 逐张产 OCR_IMAGE 事实（仅告警）；元素按页内自上而下追加在本页正文之后。
 *
 * @author cxxl
 */
public final class PdfImageEmitter {

    private PdfImageEmitter() {
    }

    /** 产出本页图片元素与事实（面积占比低于门槛的图片不产元素） */
    public static void emit(PageContent page, List<ParseElement> elements, ParseSource source,
                     String fileId, ParseProperties properties) {
        if (page.imageBoxes().isEmpty()) {
            return;
        }
        double pageArea = page.pageWidth() * page.pageHeight();
        if (pageArea <= 0) {
            return;
        }
        List<Region> boxes = page.imageBoxes().stream()
                .filter(box -> RuleLines.imageArea(box, page.pageWidth(), page.pageHeight())
                        >= pageArea * properties.getImageMinAreaRatio())
                .sorted(Comparator.comparingDouble(Region::top))
                .toList();
        int index = 0;
        for (Region box : boxes) {
            double ratio = RuleLines.imageArea(box, page.pageWidth(), page.pageHeight()) / pageArea;
            ParseElement image = ParseElement.of("p" + page.pageNo() + "img" + index, ElementType.IMAGE);
            image.setPage(page.pageNo());
            image.setAssetRef("page" + page.pageNo() + "-image" + index);
            image.setNeedsOcr(true);
            image.setBbox(new BBox(box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top()));
            image.setProvenance(new Provenance(fileId,
                    "pdf#page(" + page.pageNo() + ")/image[" + index + "]"));
            elements.add(image);
            ParseFact fact = new ParseFact();
            fact.setType(SignalType.OCR_IMAGE.name());
            fact.setRegion("page " + page.pageNo() + " image " + index);
            fact.setEvidence("嵌入图片仅引用无文字（占页面积 " + String.format("%.2f", ratio) + "）");
            source.getFacts().add(fact);
            index++;
        }
    }
}
