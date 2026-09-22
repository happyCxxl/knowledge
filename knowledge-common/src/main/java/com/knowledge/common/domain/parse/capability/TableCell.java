package com.knowledge.common.domain.parse.capability;

import lombok.Data;

/**
 * 表格单元格（结构识别结果单元）。
 *
 * @author cxxl
 */
@Data
public class TableCell {

    /** 行号（从 0 起） */
    private Integer row;

    /** 列号（从 0 起） */
    private Integer col;

    /** 行跨度 */
    private Integer rowSpan;

    /** 列跨度 */
    private Integer colSpan;

    /** 单元格文本 */
    private String text;

    /** 是否表头 */
    private Boolean isHeader;
}
