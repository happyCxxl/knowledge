package com.knowledge.worker.parser.pdf.assemble;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.PageLine;

import java.util.List;

/**
 * 表格两条路径（线框 / 文本）共用的门限与降级留痕：
 * 假表门限判定，以及规则失败时「出 TABLE 事实 + 区域降级为段落」的统一口径。
 *
 * @author cxxl
 */
final class TableSupport {

    private TableSupport() {
    }

    /** 假表门限：非空单元格占比低于阈值即判为空白网格（弃表） */
    static boolean blankTable(List<List<String>> rowTexts, ParseProperties properties) {
        int total = 0;
        int filled = 0;
        for (List<String> row : rowTexts) {
            for (String text : row) {
                total++;
                if (StrUtil.isNotBlank(text)) {
                    filled++;
                }
            }
        }
        return total > 0 && (double) filled / total < properties.getTableMinFilledRatio();
    }

    /** 表格规则失败：出 TABLE 事实并把区域降级为段落 */
    static void degrade(List<PageLine> lines, PageContent page, List<ParseElement> elements,
                        ParseSource source, String fileId, String evidence) {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.TABLE.name());
        fact.setRegion("page " + page.pageNo());
        fact.setEvidence(evidence);
        source.getFacts().add(fact);
        elements.add(PdfBodyAssembler.toParagraphElement(lines, fileId));
    }
}
