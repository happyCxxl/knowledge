package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.ParseControlService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.ParseStatsSupport;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.dto.response.parse.ParseDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析控制面服务实现。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseControlServiceImpl implements ParseControlService {

    private final KbPipelineProductDbService pipelineProductDbService;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final FileStorage fileStorage;
    private final FileResultAccessGuard accessGuard;

    /**
     * 触发解析（首次解析与失败重跑同一入口）四分支：
     * <ol>
     *   <li>RUNNING → 拒绝（40431，进行中勿重复触发）；</li>
     *   <li>QUEUED → 已登记未入队（登记与入队间崩溃的窗口），直接补投唤醒，不新建任务；</li>
     *   <li>SUCCESS/PARTIAL_SUCCESS → 拒绝（40437，成功后禁止重跑）；</li>
     *   <li>FAILED/CANCELLED/无任务 → 新建 PARSE 任务（QUEUED）落库并入队。</li>
     * </ol>
     * 旧任务/旧产物不动（重跑 = 新任务，历史可查）；完成后停在终态，不自动触发组装。
     *
     * @param fileResultId 文件结果 ID
     * @return 触发响应（新登记/已有任务 ID）
     */
    @Override
    public StageTriggerVO parse(Long fileResultId) {
        accessGuard.requireExisting(fileResultId);
        // 手动逐环节口径：本接口 = "触发解析"（首次解析 / 失败后重跑同一个入口；成功后禁止重跑）
        return triggerSupport.trigger(fileResultId, PipelineStage.PARSE, null, null, "解析", true);
    }

    @Override
    public ParseDetailVO parseDetail(Long fileResultId, Long taskId) {
        accessGuard.requireExisting(fileResultId);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.PARSE, taskId, "解析");

        ParseDetailVO vo = new ParseDetailVO();
        detailSupport.withTask(vo, fileResultId, task);

        // 产物引用/告警/统计：按 task.productId 精确取该次运行的产物（历史任务同样可展示自己的产物；无任务/无产物留空）
        // 解析的统计结构固定（LineageParseStatsVO），走 parseStats；通用 stageStats 只给没有专属结构的环节
        vo.setWarnings(new ArrayList<>());
        KbPipelineProduct product = detailSupport.productOfTask(task);
        if (NullUtil.isNotNull(product)) {
            detailSupport.withProductRef(vo, product);
            ParseResult parseResult = ParseStatsSupport.readArtifact(fileStorage, ObjectRef.ofProduct(product));
            vo.setWarnings(warningsOf(parseResult));
            vo.setParseStats(ParseStatsSupport.stats(vo.getStartedAt(), vo.getFinishedAt(), parseResult));
            vo.setParseSummary(ParseStatsSupport.summary(vo.getErrorMsg(), vo.getParseStats(), vo.getStatus()));
        }
        return vo;
    }

    /** 产物本体 → 告警文案（展示用；无产物或无告警返回空列表） */
    private List<String> warningsOf(ParseResult parseResult) {
        if (NullUtil.isNull(parseResult) || NullUtil.isNull(parseResult.getQuality())) {
            return new ArrayList<>();
        }
        return parseResult.getQuality().getWarnings().stream()
                .map(w -> w.getLevel() + " " + w.getCode() + ": " + w.getMessage())
                .toList();
    }
}
