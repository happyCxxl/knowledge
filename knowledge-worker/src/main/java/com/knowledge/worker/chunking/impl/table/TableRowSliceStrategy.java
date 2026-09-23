package com.knowledge.worker.chunking.impl.table;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import com.knowledge.common.enums.chunk.ChunkRoute;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 表格切片器（招投标行规核心）：按行/行组切、不拆散单元格；评分项与分值同属一行不分离；
 * 跨页续表在组装环节已接续成单表，整表自然切。短行（< groupThreshold）每 groupSize 行一组，长行单行一片。
 * 片内容为合法 Markdown 表格（有表头：表头行 + 分隔行 + 数据行；拆片重复表头）。
 *
 * @author cxxl
 */
@Component
public class TableRowSliceStrategy implements SliceStrategy {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.TABLE_ROW_SLICE;
    }

    @Override
    public List<Chunk> slice(ViewElement element, SliceContext context) {
        TableMarkdownSupport.TablePrep prep = TableMarkdownSupport.prepare(element, context);
        if (ObjectUtil.isNull(prep)) {
            return List.of();
        }
        ChunkRouteConfig tableConfig = context.getStrategy().route(ChunkRoute.TABLE);
        int groupThreshold = tableConfig.intParam(ChunkParamKeys.GROUP_THRESHOLD, 30);
        int groupSize = tableConfig.intParam(ChunkParamKeys.GROUP_SIZE, 3);
        return TableMarkdownSupport.rowSlice(prep.rows(), prep.headerByCol(), element, prep.table(), context,
                groupThreshold, groupSize);
    }
}
