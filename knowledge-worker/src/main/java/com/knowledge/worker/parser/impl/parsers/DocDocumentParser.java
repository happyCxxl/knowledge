package com.knowledge.worker.parser.impl.parsers;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.FontInfo;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.worker.parser.ParseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableIterator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * DOC（HWPF 二进制）原生结构解析器（POI 路径）。
 * 能力弱于 DOCX：表格合并信息读不到，只取行列文本；嵌入图片按序记录引用（定位到段落成本高）。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class DocDocumentParser extends AbstractPoiDocumentParser {

    @Override
    public boolean supports(String mimeType) {
        return FileFormat.DOC.getMimeType().equals(mimeType);
    }

    @Override
    public ParseSource parse(ParseContext context) {
        byte[] data = ParserStreamSupport.readAll(context);
        ParseSource source = ParseSource.nativeSource(capabilityName() + "-" + capabilityVersion());
        String fileId = context.getFileRef().getFileId();
        parseDoc(source, data, fileId);
        return source;
    }

    /** DOC（HWPF 二进制）主流程：段落（表格内段落跳过，由 TableIterator 输出）+ 图片引用 + 表格。 */
    private void parseDoc(ParseSource source, byte[] data, String fileId) {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(data))) {
            Range range = doc.getRange();
            int paragraphIndex = 0;
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph paragraph = range.getParagraph(i);
                if (paragraph.isInTable()) {
                    continue; // 表格内容由 TableIterator 统一输出
                }
                ParseElement element = toDocParagraphElement(paragraph, paragraphIndex, fileId);
                if (ObjectUtil.isNotNull(element)) {
                    source.getElements().add(element);
                }
                paragraphIndex++;
            }
            // 嵌入图片（PicturesTable 全表引用；定位到段落成本高，按序记录）
            PicturesTable pictures = doc.getPicturesTable();
            for (int p = 0; p < pictures.getAllPictures().size(); p++) {
                Picture picture = pictures.getAllPictures().get(p);
                ParseElement image = ParseElement.of("img" + p, ElementType.IMAGE);
                image.setAssetRef(StrUtil.blankToDefault(picture.getDescription(), "image-" + p));
                image.setNeedsOcr(true);
                image.setProvenance(new Provenance(fileId, "office#document/picture[" + p + "]"));
                source.getElements().add(image);
                source.getFacts().add(fact("picture[" + p + "]", "嵌入图片仅引用无文字"));
            }
            // 表格（HWPF 结构支持弱；合并信息读不到，只取行列文本）
            TableIterator tableIterator = new TableIterator(range);
            int tableIndex = 0;
            while (tableIterator.hasNext()) {
                Table table = tableIterator.next();
                source.getElements().add(toDocTableElement(table, tableIndex, fileId));
                tableIndex++;
            }
            source.setUnitCount(1);
        } catch (Exception e) {
            log.warn("DOC 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("DOC 解析失败: " + e.getMessage(), e);
        }
    }

    /** DOC 段落元素：字体事实取首 run；HWPF 字号为半点单位，换算 pt。 */
    private ParseElement toDocParagraphElement(Paragraph paragraph, int index, String fileId) {
        String text = paragraph.text().trim();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        ParseElement element = ParseElement.of("p" + index, ElementType.PARAGRAPH);
        element.setText(text);
        if (paragraph.numCharacterRuns() > 0) {
            CharacterRun run = paragraph.getCharacterRun(0);
            FontInfo font = new FontInfo();
            font.setName(run.getFontName());
            // HWPF 字号单位为半点（half-point），换算 pt
            int halfPoints = run.getFontSize();
            font.setSize(halfPoints > 0 ? halfPoints / 2.0 : null);
            font.setBold(run.isBold());
            element.setFont(font);
        }
        element.setProvenance(new Provenance(fileId, "office#document/paragraph[" + index + "]"));
        return element;
    }

    /** DOC 表格元素（HWPF 结构支持弱：合并信息读不到，只取行列文本）。 */
    private ParseElement toDocTableElement(Table table, int tableIndex, String fileId) {
        ParseElement tableElement = ParseElement.of("t" + tableIndex, ElementType.TABLE);
        tableElement.setRows(table.numRows());
        tableElement.setHeaderRow(0);
        for (int r = 0; r < table.numRows(); r++) {
            org.apache.poi.hwpf.usermodel.TableRow row = table.getRow(r);
            for (int c = 0; c < row.numCells(); c++) {
                ParseElement cellElement = ParseElement.of("t" + tableIndex + "c" + r + "_" + c,
                        ElementType.TABLE_CELL);
                cellElement.setText(row.getCell(c).text().trim());
                cellElement.setRow(r);
                cellElement.setCol(c);
                cellElement.setIsHeader(r == 0);
                cellElement.setProvenance(new Provenance(fileId,
                        "office#document/table[" + tableIndex + "]/cell[" + r + "," + c + "]"));
                if (tableElement.getCells() == null) {
                    tableElement.setCells(new java.util.ArrayList<>());
                }
                tableElement.getCells().add(cellElement);
            }
        }
        return tableElement;
    }
}
