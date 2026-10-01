package com.knowledge.worker;

/**
 * 环节策略的公共元信息契约：类型 / 名称 / 版本三项。
 *
 * <p>切片、向量化、预处理三个环节的策略类字段与 setter 完全同形，但彼此无关；
 * 实现本接口后，"按策略版本行回填元信息"这件事即可在调用侧共用一处
 * （见 knowledge-biz 的 {@code StageStrategySupport#bindMeta}），不必各写一遍 setter。
 *
 * <p>只声明元信息三项，不含各环节自身的策略字段 —— 本接口只解决"同形"，不介入各环节语义。
 *
 * @author cxxl
 */
public interface StageStrategy {

    void setType(String type);

    void setName(String name);

    void setVersion(String version);
}
