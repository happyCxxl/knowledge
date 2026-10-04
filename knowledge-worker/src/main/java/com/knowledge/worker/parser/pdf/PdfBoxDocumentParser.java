package com.knowledge.worker.parser.pdf;

import com.knowledge.common.domain.parse.PageDimension;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.pdf.assemble.PdfBodyAssembler;
import com.knowledge.worker.parser.pdf.assemble.PdfImageEmitter;
import com.knowledge.worker.parser.pdf.detect.HeaderFooterDetector;
import com.knowledge.worker.parser.pdf.extract.PdfPageExtractor;
import com.knowledge.worker.parser.pdf.layout.PageLayoutAnalyzer;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.support.ParserStreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDFBox 原生解析器（数字 PDF）：编排三段 —— 逐页提取（{@link PdfPageExtractor}）→
 * 页眉页脚识别（{@link HeaderFooterDetector}）→ 逐页装配元素（{@link PdfBodyAssembler} +
 * {@link RuledTableBuilder}/{@link TextTableBuilder} + {@link PdfImageEmitter}）。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class PdfBoxDocumentParser implements DocumentParserPort {

    private static final String CAPABILITY = "pdfbox";
    private static final String CAPABILITY_VERSION = "3.0.4";

    private final PdfPageExtractor pageExtractor;

    public PdfBoxDocumentParser(PageLayoutAnalyzer layoutAnalyzer) {
        this.pageExtractor = new PdfPageExtractor(layoutAnalyzer);
    }

    @Override
    public boolean supports(String mimeType) {
        return FileFormat.PDF.getMimeType().equals(mimeType);
    }

    @Override
    public String capabilityName() {
        return CAPABILITY;
    }

    @Override
    public String capabilityVersion() {
        return CAPABILITY_VERSION;
    }

    @Override
    public ParseSource parse(ParseContext context) {
        byte[] data = ParserStreamSupport.readAll(context);
        String fileId = context.getFileRef().getFileId();
        ParseSource source = ParseSource.nativeSource(CAPABILITY + "-" + CAPABILITY_VERSION);
        try (PDDocument doc = Loader.loadPDF(data)) {
            int pageCount = doc.getNumberOfPages();
            source.setUnitCount(pageCount);
            context.getFileRef().setPageCount(pageCount);

            // ① 逐页提取：字符 + 行 + 阅读顺序 + 页级指标
            List<PageContent> pages = new ArrayList<>();
            for (int p = 1; p <= pageCount; p++) {
                PageContent pageContent = pageExtractor.extractPage(doc, p, context);
                pages.add(pageContent);
                source.getPageMetrics().add(pageContent.metric());
                source.getPageDimensions().add(new PageDimension(pageContent.pageNo(),
                        pageContent.pageWidth(), pageContent.pageHeight()));
            }

            // ② HEADER/FOOTER 识别：页顶/页底区域内跨页重复文本与页码模式（先算，正文聚合时排除）
            Set<String> headers = HeaderFooterDetector.detectHeaders(pages, context);
            HeaderFooterDetector.FooterKeys footers = HeaderFooterDetector.detectFooters(pages, context);

            // ③ 逐页装配元素：页眉页脚 → 正文（段落与两条表格路径）→ 页内图片
            List<ParseElement> elements = new ArrayList<>();
            for (PageContent page : pages) {
                PdfBodyAssembler.assemble(page, headers, footers, elements, source, fileId,
                        context.getProperties());
                PdfImageEmitter.emit(page, elements, source, fileId, context.getProperties());
            }
            source.setElements(elements);
        } catch (Exception e) {
            log.warn("PDF 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("PDF 解析失败: " + e.getMessage(), e);
        }
        return source;
    }
}
