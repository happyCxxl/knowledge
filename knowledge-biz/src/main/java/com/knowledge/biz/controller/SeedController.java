package com.knowledge.biz.controller;

import com.knowledge.biz.seed.SeedResetService;
import com.knowledge.biz.seed.SeedService;
import com.knowledge.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示数据种子入口（**临时代码，演示数据灌好后应删除**）。
 *
 * <p>为什么用 HTTP 入口而不是 CommandLineRunner：种子需要跑在**已完整启动**的应用里
 * （要连 MySQL / 本地产物目录 / Milvus），而且执行时机由人控制更好 ——
 * CommandLineRunner 会在每次启动时都尝试灌数据。
 *
 * @author cxxl
 */
@RestController
@RequestMapping("/seed")
@RequiredArgsConstructor
public class SeedController {

    private final SeedService seedService;
    private final SeedResetService seedResetService;

    /**
     * 为指定知识库灌一条「上传 → 向量化 → 索引构建」的完整链路。
     *
     * @param knowledgeBaseId 目标知识库（需已绑定 PREPROCESS/CHUNK/EMBED 三条策略）
     * @return 本次产出的关键 ID
     */
    @PostMapping("/pipeline")
    public R<Map<String, Object>> seedPipeline(@RequestParam Long knowledgeBaseId) {
        return R.ok(seedService.seed(knowledgeBaseId));
    }

    /**
     * 删除指定知识库下由种子产生的数据（让种子可重复执行）。
     *
     * <p>只删种子 ID 段内的行，不动真实数据；**不删 Milvus 集合**（那是索引回收流程的事）。
     *
     * @param knowledgeBaseId 目标知识库
     * @return 各表删除行数
     */
    @PostMapping("/reset")
    public R<Map<String, Integer>> reset(@RequestParam Long knowledgeBaseId) {
        return R.ok(seedResetService.reset(knowledgeBaseId));
    }
}
