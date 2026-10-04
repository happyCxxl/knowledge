package com.knowledge.worker.parser.layout;

import java.util.List;

/**
 * 分栏与阅读顺序端口：按页面几何事实找栏沟、定阅读顺序。
 * 规则实现见 impl.ProjectionPageLayoutAnalyzer；接入版面模型或外部服务时另加实现，解析器不改。
 *
 * @author cxxl
 */
public interface PageLayoutAnalyzer {

    /**
     * 找栏沟：按盒子的横向覆盖找宽度达标的空白带，并校验每栏占比与字符数。
     *
     * @param chars      字符级几何事实
     * @param properties 判定阈值
     * @return 栏沟中心 x（按 x 升序；空表示单栏）
     */
    List<Double> gutters(PageLayoutInput chars, LayoutProperties properties);

    /**
     * 定阅读顺序：跨栏行把页面切成上下若干带，带内跨栏行在前、其余按栏序与栏内纵向顺序。
     *
     * @param lines   行级几何事实（已按栏切开；跨栏行横跨栏沟）
     * @param gutters 栏沟中心 x（空表示单栏，返回顺序即纵向顺序）
     * @return 阅读顺序与每行栏号
     */
    PageLayoutResult order(PageLayoutInput lines, List<Double> gutters);
}
