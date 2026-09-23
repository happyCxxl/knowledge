package com.knowledge.worker.chunking.impl.table;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import com.knowledge.common.enums.chunk.ChunkRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表格切片器（行组）：数据行依次入组，行数 ≥ groupSize 或组内字符累计 ≥ maxLen 即结算成片；
 * 每片合法 Markdown 表格（表头随片）。中间粒度：比行级切片更大、比整表更细。
 *
 * @author cxxl
 */
@Component
public class TableRowGroupStrategy implements SliceStrategy {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.TABLE_ROW_GROUP;
    }

    @Override
    public List<Chunk> slice(ViewElement element, SliceContext context) {
        TableMarkdownSupport.TablePrep prep = TableMarkdownSupport.prepare(element, context);
        if (ObjectUtil.isNull(prep)) {
            return List.of();
        }
        TableMarkdownSupport.TableRows prepared = prep.rows();
        Map<Integer, String> headerByCol = prep.headerByCol();
        UnifiedElement table = prep.table();

        ChunkRouteConfig tableConfig = context.getStrategy().route(ChunkRoute.TABLE);
        int groupSize = tableConfig.intParam(ChunkParamKeys.GROUP_SIZE, 5);
        int maxLen = tableConfig.intParam(ChunkParamKeys.MAX_LEN, 600);

        List<Chunk> chunks = new ArrayList<>();
        List<Map<Integer, String>> groupBuffer = new ArrayList<>();
        List<List<String>> groupIds = new ArrayList<>();
        int groupLength = 0;
        for (int i = 0; i < prepared.rows.size(); i++) {
            Map<Integer, String> row = prepared.rows.get(i);
            int rowLength = row.values().stream().mapToInt(String::length).sum();
            if (!groupBuffer.isEmpty()
                    && (groupBuffer.size() >= groupSize || groupLength + rowLength > maxLen)) {
                chunks.add(TableMarkdownSupport.buildChunk(
                        TableMarkdownSupport.markdownContent(groupBuffer, headerByCol),
                        element, table, context, groupIds));
                groupBuffer = new ArrayList<>();
                groupIds = new ArrayList<>();
                groupLength = 0;
            }
            groupBuffer.add(row);
            groupIds.add(prepared.ids.get(i));
            groupLength += rowLength;
        }
        if (!groupBuffer.isEmpty()) {
            chunks.add(TableMarkdownSupport.buildChunk(
                    TableMarkdownSupport.markdownContent(groupBuffer, headerByCol),
                    element, table, context, groupIds));
        }
        return chunks;
    }
}
