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
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DOCX 原生结构解析器（POI 路径）。
 * 产出 native 路：段落（含样式与字体事实，无样式标题不猜层级）、表格（行列/合并/isHeader 候选）、
 * 图片引用（仅引用+needsOcr）、页眉页脚部件、Office 结构路径溯源。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class DocxDocumentParser extends AbstractPoiDocumentParser {

    @Override
    public boolean supports(String mimeType) {
        return FileFormat.DOCX.getMimeType().equals(mimeType);
    }

    /** DOCX 主流程：按 body 顺序产出段落/表格元素 + 段落内嵌图片引用 + 页眉页脚部件。 */
    @Override
    protected void parseNative(ParseSource source, byte[] data, ParseContext context) {
        String fileId = context.getFileRef().getFileId();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            List<IBodyElement> bodyElements = doc.getBodyElements();
            int paragraphIndex = 0;
            int tableIndex = 0;
            for (IBodyElement bodyElement : bodyElements) {
                if (bodyElement instanceof XWPFParagraph paragraph) {
                    ParseElement element = toDocxParagraphElement(paragraph, paragraphIndex, fileId);
                    if (ObjectUtil.isNotNull(element)) {
                        source.getElements().add(element);
                    }
                    // 段落内嵌图片：仅记引用 + needsOcr（OCR 预留）
                    appendDocxPictures(source, paragraph, paragraphIndex, fileId);
                    paragraphIndex++;
                } else if (bodyElement instanceof XWPFTable table) {
                    source.getElements().add(toDocxTableElement(table, tableIndex, fileId));
                    tableIndex++;
                }
            }
            // 页眉页脚部件（DOCX header/footer parts）：独立产出 HEADER/FOOTER 元素，处置归预处理环节
            appendDocxHeaderFooter(source, doc, fileId);
            source.setUnitCount(1);
        } catch (Exception e) {
            log.warn("DOCX 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("DOCX 解析失败: " + e.getMessage(), e);
        }
    }

    /** DOCX 页眉页脚部件 → HEADER/FOOTER 元素；单部件异常不阻断整档解析 */
    private void appendDocxHeaderFooter(ParseSource source, XWPFDocument doc, String fileId) {
        appendParts(source, doc.getHeaderList(), PartKind.HEADER, fileId);
        appendParts(source, doc.getFooterList(), PartKind.FOOTER, fileId);
    }

    /** 单个部件列表 → 元素（部件文本非空才产出；按部件出现顺序编号） */
    private void appendParts(ParseSource source, List<? extends XWPFHeaderFooter> parts, PartKind kind,
                             String fileId) {
        int index = 0;
        try {
            for (XWPFHeaderFooter part : parts) {
                String text = part.getParagraphs().stream()
                        .map(XWPFParagraph::getText)
                        .filter(StrUtil::isNotBlank)
                        .reduce("", String::concat);
                if (StrUtil.isNotBlank(text)) {
                    ParseElement element = ParseElement.of(kind.idPrefix() + index, kind.elementType());
                    element.setText(text);
                    element.setProvenance(new Provenance(fileId, kind.provenancePrefix() + index + "]"));
                    source.getElements().add(element);
                }
                index++;
            }
        } catch (Exception e) {
            log.warn(kind.errorLog() + ", fileId={}", fileId, e);
        }
    }

    /** 页眉/页脚部件口径：id 前缀 / 元素类型 / 溯源前缀 / 读取失败告警文案 */
    private record PartKind(String idPrefix, ElementType elementType, String provenancePrefix, String errorLog) {

        private static final PartKind HEADER =
                new PartKind("h", ElementType.HEADER, "docx#header[", "DOCX 页眉部件读取失败");

        private static final PartKind FOOTER =
                new PartKind("f", ElementType.FOOTER, "docx#footer[", "DOCX 页脚部件读取失败");
    }

    /** DOCX 段落元素：空段跳过；样式名 + 首个有效 run 的字体事实（无样式标题只输出事实，层级归组装环节）。 */
    private ParseElement toDocxParagraphElement(XWPFParagraph paragraph, int index, String fileId) {
        String text = paragraph.getText();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        ParseElement element = ParseElement.of("p" + index, ElementType.PARAGRAPH);
        element.setText(text);
        element.setStyle(paragraph.getStyleID());
        element.setTocCandidate(TocLineFeature.isTocLine(text));
        // 字体事实：取首个有数据的 run（无样式标题也只输出事实，层级归组装环节）
        for (XWPFRun run : paragraph.getRuns()) {
            if (ObjectUtil.isNull(run)) {
                continue;
            }
            FontInfo font = new FontInfo();
            font.setName(run.getFontFamily());
            Double size = run.getFontSizeAsDouble();
            font.setSize(ObjectUtil.isNotNull(size) && size > 0 ? size : null);
            font.setBold(ObjectUtil.isNotNull(run.isBold()) ? run.isBold() : null);
            element.setFont(font);
            break;
        }
        element.setProvenance(new Provenance(fileId, "office#document.xml/paragraph[" + index + "]"));
        return element;
    }

    /** 段落内嵌图片：仅记引用 + needsOcr + OCR_IMAGE 事实（一期不识别文字）。 */
    private void appendDocxPictures(ParseSource source, XWPFParagraph paragraph, int index, String fileId) {
        int pictureIndex = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            if (ObjectUtil.isNull(run) || run.getEmbeddedPictures().isEmpty()) {
                continue;
            }
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                String assetRef = picture.getPictureData().getFileName();
                ParseElement image = ParseElement.of("img" + index + "_" + pictureIndex, ElementType.IMAGE);
                image.setAssetRef(assetRef);
                image.setNeedsOcr(true);
                image.setProvenance(new Provenance(fileId,
                        "office#document.xml/paragraph[" + index + "]/pic[" + pictureIndex + "]"));
                source.getElements().add(image);
                source.getFacts().add(fact("paragraph[" + index + "]", "嵌入图片仅引用无文字（drawing 节点）"));
                pictureIndex++;
            }
        }
    }

    /** DOCX 表格元素：gridSpan 占位格跳过、vMerge 列内 continue 计数，结束回填 restart 单元格行跨度。 */
    private ParseElement toDocxTableElement(XWPFTable table, int tableIndex, String fileId) {
        ParseElement tableElement = ParseElement.of("t" + tableIndex, ElementType.TABLE);
        List<XWPFTableRow> rows = table.getRows();
        tableElement.setRows(rows.size());
        int maxCols = rows.stream().mapToInt(r -> r.getTableCells().size()).max().orElse(0);
        tableElement.setCols(maxCols);
        tableElement.setHeaderRow(0);
        // vMerge：列内连续 continue 计数（行跨度）；gridSpan 横向合并的占位格跳过
        Map<Integer, Integer> columnMergePending = new HashMap<>();
        int rowIndex = 0;
        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            int colIndex = 0;
            int skipRemaining = 0;
            for (XWPFTableCell cell : cells) {
                CTTcPr tcPr = cell.getCTTc().getTcPr();
                int gridSpan = ObjectUtil.isNotNull(tcPr) && ObjectUtil.isNotNull(tcPr.getGridSpan())
                        ? tcPr.getGridSpan().getVal().intValue() : 1;
                STMerge.Enum vMerge = ObjectUtil.isNotNull(tcPr) && ObjectUtil.isNotNull(tcPr.getVMerge())
                        ? tcPr.getVMerge().getVal() : null;
                if (skipRemaining > 0) {
                    // 横向合并占位格：不产出元素（列号继续推进）
                    skipRemaining--;
                    if (STMerge.CONTINUE.equals(vMerge) && columnMergePending.containsKey(colIndex)) {
                        columnMergePending.merge(colIndex, 1, Integer::sum);
                    }
                    colIndex += gridSpan;
                    continue;
                }
                if (ObjectUtil.isNull(vMerge) || STMerge.RESTART.equals(vMerge)) {
                    ParseElement cellElement = ParseElement.of("t" + tableIndex + "c" + rowIndex + "_" + colIndex,
                            ElementType.TABLE_CELL);
                    cellElement.setText(cell.getText());
                    cellElement.setRow(rowIndex);
                    cellElement.setCol(colIndex);
                    cellElement.setColSpan(gridSpan > 1 ? gridSpan : null);
                    cellElement.setIsHeader(rowIndex == 0);
                    cellElement.setProvenance(new Provenance(fileId,
                            "office#document.xml/table[" + tableIndex + "]/cell[" + rowIndex + "," + colIndex + "]"));
                    appendCell(tableElement, cellElement);
                    if (STMerge.RESTART.equals(vMerge)) {
                        columnMergePending.put(colIndex, 1);
                    }
                } else if (STMerge.CONTINUE.equals(vMerge) && columnMergePending.containsKey(colIndex)) {
                    columnMergePending.merge(colIndex, 1, Integer::sum);
                }
                if (gridSpan > 1) {
                    skipRemaining = gridSpan - 1;
                }
                colIndex += gridSpan;
            }
            rowIndex++;
        }
        // 回填 vMerge 行跨度（restart 单元格）
        for (ParseElement cell : tableElement.getCells()) {
            Integer pending = columnMergePending.get(cell.getCol());
            if (ObjectUtil.isNotNull(pending) && pending > 1) {
                cell.setRowSpan(pending);
            }
        }
        return tableElement;
    }
}
