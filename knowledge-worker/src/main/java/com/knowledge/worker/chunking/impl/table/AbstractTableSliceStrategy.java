package com.knowledge.worker.chunking.impl.table;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.chunk.ChunkRoute;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;

import java.util.List;

/**
 * 表格切片器公共骨架：表格路四个算法（行级 / 行组 / 整表 / 表+引导段）都先取"结构表 + 表头映射 + 数据行"，
 * 取不到（元素或单元格缺失）一律空片；子类只实现按已备好表格的切分。
 *
 * @author cxxl
 */
public abstract class AbstractTableSliceStrategy implements SliceStrategy {

    @Override
    public final List<Chunk> slice(ViewElement element, SliceContext context) {
        TableMarkdownSupport.TablePrep prep = TableMarkdownSupport.prepare(element, context);
        if (ObjectUtil.isNull(prep)) {
            return List.of();
        }
        return slicePrepared(prep, element, context);
    }

    /**
     * 按已备好的表格切分（prep 非空：结构表、表头映射、数据行均可用）。
     *
     * @param prep    表格准备结果
     * @param element 视图元素（检索文本口径）
     * @param context 切片上下文（含策略路由与标题路径）
     */
    protected abstract List<Chunk> slicePrepared(TableMarkdownSupport.TablePrep prep,
                                                 ViewElement element, SliceContext context);

    /** 表格路由参数（TABLE 路，解析时已补默认值） */
    protected final ChunkRouteConfig tableRoute(SliceContext context) {
        return context.getStrategy().route(ChunkRoute.TABLE);
    }
}
