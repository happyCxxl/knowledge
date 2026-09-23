package com.knowledge.biz.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 检索运行记录开关（step-14 B6，B09）：测试台检索必记；
 * 生产检索按 Nacos 配置 `retrieval.run.record-production`（默认关，逐查询记录量大）。
 *
 * @author cxxl
 */
@Component
public class RetrievalRunSettings {

    private final boolean recordProduction;

    public RetrievalRunSettings(@Value("${retrieval.run.record-production:false}") boolean recordProduction) {
        this.recordProduction = recordProduction;
    }

    /** 生产检索是否落运行记录 */
    public boolean recordProduction() {
        return recordProduction;
    }
}
