package com.knowledge.worker.parser.office.impl;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.office.AbstractPoiDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFPictureShape;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTable;
import org.apache.poi.hslf.usermodel.HSLFTableCell;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PPT / PPTX 解析器（POI XSLF 与 HSLF 两条读取路径）：
 * 产出 native 路：每张幻灯片一条单元指标（页单位 = 幻灯片序号），形状按类型转元素 —— 文本框与占位符文字成段落、
 * 表格成 TABLE（行列与表头判定复用 Office 统一口径）、图片成 IMAGE（引用 + needsOcr）并按页记一条 OCR_IMAGE 事实。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class PptDocumentParser extends AbstractPoiDocumentParser {

    @Override
    public boolean supports(String mime) {
        return FileFormat.PPT.getMimeType().equals(mime) || FileFormat.PPTX.getMimeType().equals(mime);
    }

    @Override
    protected void parseNative(ParseSource source, byte[] data, ParseContext context) {
        String fileId = context.getFileRef().getFileId();
        String fileName = StrUtil.blankToDefault(context.getFileRef().getFileName(), "");
        boolean legacy = fileName.toLowerCase(Locale.ROOT).endsWith(".ppt");
        try {
            if (legacy) {
                parsePpt(source, data, fileId);
            } else {
                parsePptx(source, data, fileId);
            }
        } catch (Exception e) {
            log.warn("演示文稿解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("演示文稿解析失败: " + e.getMessage(), e);
        }
    }

    /** PPTX（OOXML）路径 */
    private void parsePptx(ParseSource source, byte[] data, String fileId) throws Exception {
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(data))) {
            List<String> unitTexts = new ArrayList<>();
            List<Integer> unitImages = new ArrayList<>();
            int slideNo = 0;
            for (XSLFSlide slide : show.getSlides()) {
                slideNo++;
                StringBuilder text = new StringBuilder();
                int paragraphs = 0;
                int tables = 0;
                int pictures = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String shapeText = StrUtil.trimToEmpty(textShape.getText());
                        if (StrUtil.isNotBlank(shapeText)) {
                            text.append(shapeText);
                            source.getElements().add(paragraphElement("p" + slideNo + "_" + paragraphs, shapeText,
                                    slideNo, "office#slide[" + slideNo + "]/text[" + paragraphs + "]", fileId));
                            paragraphs++;
                        }
                    } else if (shape instanceof XSLFTable table) {
                        ParseElement tableElement = tableElement("t" + slideNo + "_" + tables, slideNo,
                                "office#slide[" + slideNo + "]/table[" + tables + "]", fileId);
                        fillXslfTable(tableElement, table);
                        source.getElements().add(tableElement);
                        text.append(tableText(tableElement));
                        tables++;
                    } else if (shape instanceof XSLFPictureShape picture) {
                        XSLFPictureData pictureData = picture.getPictureData();
                        source.getElements().add(imageElement("img" + slideNo + "_" + pictures,
                                "image-" + pictures + "." + pictureSuffix(pictureData), slideNo,
                                "office#slide[" + slideNo + "]/picture[" + pictures + "]", fileId));
                        pictures++;
                    }
                }
                if (pictures > 0) {
                    source.getFacts().add(fact("slide[" + slideNo + "]",
                            "嵌入图片仅引用无文字（本页 " + pictures + " 张）"));
                }
                unitTexts.add(text.toString());
                unitImages.add(pictures);
            }
            fillUnitMetrics(source, unitTexts, unitImages);
        }
    }

    /** PPT（二进制）路径：形状接口与 OOXML 同族，取文本与表格的方式一致 */
    private void parsePpt(ParseSource source, byte[] data, String fileId) throws Exception {
        try (HSLFSlideShow show = new HSLFSlideShow(new ByteArrayInputStream(data))) {
            List<String> unitTexts = new ArrayList<>();
            List<Integer> unitImages = new ArrayList<>();
            int slideNo = 0;
            for (HSLFSlide slide : show.getSlides()) {
                slideNo++;
                StringBuilder text = new StringBuilder();
                int paragraphs = 0;
                int tables = 0;
                int pictures = 0;
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String shapeText = StrUtil.trimToEmpty(textShape.getText());
                        if (StrUtil.isNotBlank(shapeText)) {
                            text.append(shapeText);
                            source.getElements().add(paragraphElement("p" + slideNo + "_" + paragraphs, shapeText,
                                    slideNo, "office#slide[" + slideNo + "]/text[" + paragraphs + "]", fileId));
                            paragraphs++;
                        }
                    } else if (shape instanceof HSLFTable table) {
                        ParseElement tableElement = tableElement("t" + slideNo + "_" + tables, slideNo,
                                "office#slide[" + slideNo + "]/table[" + tables + "]", fileId);
                        fillHslfTable(tableElement, table);
                        source.getElements().add(tableElement);
                        text.append(tableText(tableElement));
                        tables++;
                    } else if (shape instanceof HSLFPictureShape picture) {
                        HSLFPictureData pictureData = picture.getPictureData();
                        source.getElements().add(imageElement("img" + slideNo + "_" + pictures,
                                "image-" + pictures + "." + pictureSuffix(pictureData), slideNo,
                                "office#slide[" + slideNo + "]/picture[" + pictures + "]", fileId));
                        pictures++;
                    }
                }
                if (pictures > 0) {
                    source.getFacts().add(fact("slide[" + slideNo + "]",
                            "嵌入图片仅引用无文字（本页 " + pictures + " 张）"));
                }
                unitTexts.add(text.toString());
                unitImages.add(pictures);
            }
            fillUnitMetrics(source, unitTexts, unitImages);
        }
    }

    /** OOXML 表格：行列取自实际单元格，表头判定走 Office 统一口径 */
    private void fillXslfTable(ParseElement tableElement, XSLFTable table) {
        List<XSLFTableRow> rows = table.getRows();
        tableElement.setRows(rows.size());
        int maxCols = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<XSLFTableCell> cells = rows.get(r).getCells();
            maxCols = Math.max(maxCols, cells.size());
            for (int c = 0; c < cells.size(); c++) {
                appendCell(tableElement, cellElement(r, c, StrUtil.trimToEmpty(cells.get(c).getText())));
            }
        }
        tableElement.setCols(maxCols);
        tableElement.setHeaderRow(settleHeaderRow(tableElement));
    }

    /** 二进制表格：同上（HSLF 侧单元格接口同族） */
    private void fillHslfTable(ParseElement tableElement, HSLFTable table) {
        tableElement.setRows(table.getNumberOfRows());
        int maxCols = 0;
        for (int r = 0; r < table.getNumberOfRows(); r++) {
            maxCols = Math.max(maxCols, table.getNumberOfColumns());
            for (int c = 0; c < table.getNumberOfColumns(); c++) {
                HSLFTableCell cell = table.getCell(r, c);
                appendCell(tableElement, cellElement(r, c,
                        NullUtil.isNull(cell) ? "" : StrUtil.trimToEmpty(cell.getText())));
            }
        }
        tableElement.setCols(maxCols);
        tableElement.setHeaderRow(settleHeaderRow(tableElement));
    }

    private ParseElement paragraphElement(String id, String text, int slideNo, String path, String fileId) {
        ParseElement element = ParseElement.of(id, ElementType.PARAGRAPH);
        element.setText(text);
        element.setPage(slideNo);
        element.setProvenance(new Provenance(fileId, path));
        return element;
    }

    private ParseElement tableElement(String id, int slideNo, String path, String fileId) {
        ParseElement element = ParseElement.of(id, ElementType.TABLE);
        element.setPage(slideNo);
        element.setProvenance(new Provenance(fileId, path));
        return element;
    }

    private ParseElement imageElement(String id, String assetRef, int slideNo, String path, String fileId) {
        ParseElement element = ParseElement.of(id, ElementType.IMAGE);
        element.setAssetRef(assetRef);
        element.setNeedsOcr(true);
        element.setPage(slideNo);
        element.setProvenance(new Provenance(fileId, path));
        return element;
    }

    private ParseElement cellElement(int row, int col, String text) {
        ParseElement cell = ParseElement.of(ElementType.TABLE_CELL);
        cell.setText(text);
        cell.setRow(row);
        cell.setCol(col);
        return cell;
    }

    /** 表格文本（单元指标用）：各格文本拼接 */
    private String tableText(ParseElement tableElement) {
        StringBuilder text = new StringBuilder();
        for (ParseElement cell : NullUtil.isNull(tableElement.getCells()) ? List.<ParseElement>of()
                : tableElement.getCells()) {
            text.append(StrUtil.blankToDefault(cell.getText(), ""));
        }
        return text.toString();
    }

    /** 图片扩展名：按内容类型（取不到再看字节头）推断，最终兜底 png */
    private String pictureSuffix(PictureData pictureData) {
        if (NullUtil.isNull(pictureData)) {
            return "png";
        }
        String contentType = StrUtil.blankToDefault(pictureData.getContentType(), "").toLowerCase(Locale.ROOT);
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        }
        if (contentType.contains("gif")) {
            return "gif";
        }
        if (contentType.contains("bmp")) {
            return "bmp";
        }
        if (contentType.contains("tiff")) {
            return "tiff";
        }
        if (contentType.contains("png")) {
            return "png";
        }
        return suffixByMagic(pictureData.getData());
    }

    /** 字节头兜底：PNG / JPEG / GIF / BMP 之外一律按 png */
    private String suffixByMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 3) {
            return "png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "bmp";
        }
        return "png";
    }
}
