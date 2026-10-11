package com.knowledge.worker.parser.pdf;

import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.capability.CapabilityRegistry;
import com.knowledge.worker.parser.pdf.layout.ProjectionPageLayoutAnalyzer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 表格能力接线的回归单测：注册表为 null（能力默认关）时规则路径照常出表，且不出现任何能力留痕事实 ——
 * 接线引入的入参不改变默认行为。
 *
 * @author cxxl
 */
class PdfTableCapabilityWiringTest {

    /** 两行两列宽间距文本：规则路径自己聚成 2×2 表；注册表为 null 时不接能力、不留痕 */
    @Test
    void withoutCapabilityShouldKeepRulePathUntouched() throws Exception {
        ParseSource source = parse(buildTwoColumnBlockPdf(), null);

        List<ParseElement> tables = source.getElements().stream()
                .filter(element -> ElementType.TABLE.name().equals(element.getType())).toList();
        assertEquals(1, tables.size(), () -> "elements=" + source.getElements());
        assertEquals(2, tables.getFirst().getRows());
        assertEquals(2, tables.getFirst().getCols());
        assertEquals(4, tables.getFirst().getCells().size());
        assertFalse(source.getFacts().stream().anyMatch(fact -> fact.getEvidence().contains("能力补强")),
                () -> "facts=" + source.getFacts());
    }

    private ParseSource parse(byte[] data, CapabilityRegistry registry) {
        ParseContext context = new ParseContext();
        FileReference fileRef = new FileReference();
        fileRef.setFileId("F-1");
        fileRef.setFileName("table-capability.pdf");
        fileRef.setMimeType("application/pdf");
        context.setFileRef(fileRef);
        context.setInputStream(new ByteArrayInputStream(data));
        context.setProperties(new ParseProperties());
        return new PdfBoxDocumentParser(new ProjectionPageLayoutAnalyzer(), registry).parse(context);
    }

    /** 造一页两行两列宽间距 ASCII 文本（Helvetica 装不下中文，夹具只用 ASCII） */
    private byte[] buildTwoColumnBlockPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(595, 842));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                writeText(cs, 700, 72, "Item name");
                writeText(cs, 700, 400, "1");
                writeText(cs, 680, 72, "Sub item");
                writeText(cs, 680, 400, "2");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void writeText(PDPageContentStream cs, float y, float x, String text) throws Exception {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}
