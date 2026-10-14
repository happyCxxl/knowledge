package com.knowledge.model.support;

import com.knowledge.model.adapter.ModelAdapter;
import com.knowledge.model.calllog.ModelCallLogPort;
import com.knowledge.model.calllog.ModelCallLogRecorder;
import com.knowledge.model.catalog.ModelCatalogPort;
import com.knowledge.model.catalog.StaticModelCatalog;
import com.knowledge.model.invoke.ModelErrorClassifier;
import com.knowledge.model.invoke.ModelInvoker;
import com.knowledge.model.listener.ModelCallTraceHolder;
import com.knowledge.model.validate.OutputSchemaValidator;

/**
 * 调用器装配（测试用）：按生产装配顺序把适配器、上下文持有器与调用明细端口接成一次调用的完整外壳。
 * 目录默认取静态目录（不带价格），需要价格快照的用例经重载入口传入自己的目录实现。
 *
 * @author cxxl
 */
public final class TestModelInvoker {

    private TestModelInvoker() {
    }

    /**
     * 不带定价的调用器：目录取静态目录，成本留空。
     *
     * @param adapter     适配器
     * @param traceHolder 调用上下文持有器
     * @param logPort     调用明细端口
     * @return 调用器
     */
    public static ModelInvoker of(ModelAdapter adapter, ModelCallTraceHolder traceHolder, ModelCallLogPort logPort) {
        return of(adapter, traceHolder, logPort, new StaticModelCatalog());
    }

    /**
     * 指定目录的调用器。
     *
     * @param adapter     适配器
     * @param traceHolder 调用上下文持有器
     * @param logPort     调用明细端口
     * @param catalog     模型目录（价格快照来源）
     * @return 调用器
     */
    public static ModelInvoker of(ModelAdapter adapter, ModelCallTraceHolder traceHolder, ModelCallLogPort logPort,
            ModelCatalogPort catalog) {
        return new ModelInvoker(adapter, new OutputSchemaValidator(), new ModelErrorClassifier(), traceHolder,
                new ModelCallLogRecorder(logPort, catalog));
    }
}
