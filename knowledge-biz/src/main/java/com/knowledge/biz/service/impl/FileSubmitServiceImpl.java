package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.FileSubmitService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.db.KbSubmitLogDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.InputVoAssembler;
import com.knowledge.biz.service.support.TaskVoAssembler;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.entity.KbSubmitLog;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.input.FileMetadata;
import com.knowledge.common.domain.input.FileValidationResult;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.domain.rules.StageFunnelRules;
import com.knowledge.common.dto.request.input.FileSubmitRequest;
import com.knowledge.common.dto.response.input.FileResultVO;
import com.knowledge.common.dto.response.input.FileSubmitResponse;
import com.knowledge.common.dto.response.input.SubmitLogVO;
import com.knowledge.common.dto.response.task.StageStatusVO;
import com.knowledge.common.enums.input.SubmitStatus;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.input.FileValidatorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link FileSubmitService} 实现。
 *
 * <p>实现要点：
 * <ul>
 *   <li>幂等：requestId 唯一约束 + 查询返回已有；并发冲突（DuplicateKey）转幂等回放，不报系统错误；</li>
 *   <li>归属失败（知识库不存在/停用/已删）抛 KnowledgeException（40401/40421），不记提交日志；</li>
 *   <li>文件校验不通过（含文件不存在）只记 kb_submit_log(FAIL)，正常返回 FAIL 日志；</li>
 *   <li>建档三写（kb_source_file / kb_file_result / kb_submit_log）同一 {@code @Transactional}
 *       （手动逐环节口径：不登记任务，解析由页面手动触发）；</li>
 *   <li>同一 fileId 复用 kb_source_file（uk_file_id；并发撞键时重查复用），每次提交新建 kb_file_result。</li>
 * </ul>
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSubmitServiceImpl implements FileSubmitService {

    /** 用户归属默认值（检索强制过滤口径，一期统一 ADMIN） */
    private static final String DEFAULT_OWNER = "ADMIN";

    private final KnowledgeBaseDbService knowledgeBaseDbService;
    private final KbSourceFileDbService sourceFileDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbSubmitLogDbService submitLogDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final FileValidatorPort fileValidator;
    private final FileStorage fileStorage;
    private final InputVoAssembler inputVoAssembler;
    private final TaskVoAssembler taskVoAssembler;

    /**
     * 提交文件：幂等检查 → 归属校验 → 文件校验 → 建档三写。
     *
     * <p>五步时序：
     * <ol>
     *   <li>幂等：requestId 已存在直接回放已有记录；</li>
     *   <li>归属校验：知识库存在/启用/未删除；</li>
     *   <li>文件校验：单次取流内完成 sha256 + Tika 魔数识别 + 大小双源比对 + 加密探测；</li>
     *   <li>建档三写：sourceFile 按 fileId 复用，fileResult 每次提交新建，submitLog 记录流水；</li>
     *   <li>组装响应返回。</li>
     * </ol>
     *
     * <p>边界：本方法不登记处理任务——解析由页面在文件结果页手动触发；方法整体同一事务。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request         提交请求（fileId + requestId）
     * @return 提交响应：新建提交 pipelineTaskId 为 null；
     *         幂等回放时回填该文件结果已有的解析任务 ID（可能为 null）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileSubmitResponse submit(Long knowledgeBaseId, FileSubmitRequest request) {
        ThrowUtil.throwIf(StrUtil.isBlank(request.getRequestId()), ErrorCode.REQUEST_ID_MISSING);

        // ① 幂等：同一请求重复到达返回已有记录
        KbSubmitLog existing = submitLogDbService.getByRequestId(request.getRequestId());
        if (ObjectUtil.isNotNull(existing)) {
            log.info("===> FileSubmitServiceImpl submit 幂等回放, requestId={}", request.getRequestId());
            return inputVoAssembler.toSubmitResponse(existing, findParseTaskId(existing.getFileResultId()));
        }

        // ② 归属校验：存在/启用/未删除
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(knowledgeBaseId);
        KnowledgeBaseRules.checkCanSubmit(kb);

        // ③ 文件校验：失败只记 FAIL 日志，不建结果、不建任务
        long validationStart = System.currentTimeMillis();
        FileValidationResult validationResult = fileValidator.validate(request.getFileId());
        if (!validationResult.isPassed()) {
            return recordFailAndReturn(knowledgeBaseId, request, validationResult.getFailReason().name());
        }
        log.info("===> FileSubmitServiceImpl submit 文件校验通过, kbId={}, fileId={}, format={}, size={}B, sha256={}, 校验耗时={}ms",
                knowledgeBaseId, request.getFileId(), validationResult.getFormat(),
                validationResult.getSize(), validationResult.getSha256(),
                System.currentTimeMillis() - validationStart);

        // ④ 建档三写（同一事务；不登记任务——手动逐环节口径：解析由页面触发）
        long buildStart = System.currentTimeMillis();
        FileMetadata metadata = fileStorage.metadata(request.getFileId());
        Long userId = currentUserId();

        KbSourceFile sourceFile = findOrCreateSourceFile(request, validationResult, metadata, userId);

        KbFileResult fileResult = new KbFileResult();
        fileResult.setKnowledgeBaseId(kb.getId());
        fileResult.setOwner(DEFAULT_OWNER);
        fileResult.setSourceFileId(sourceFile.getId());
        fileResult.setUserId(userId);
        fileResultDbService.save(fileResult);

        KbSubmitLog submitLog = new KbSubmitLog();
        submitLog.setKnowledgeBaseId(kb.getId());
        submitLog.setRequestId(request.getRequestId());
        submitLog.setFileId(request.getFileId());
        submitLog.setSha256(validationResult.getSha256());
        submitLog.setFileName(metadata.getFileName());
        submitLog.setFileResultId(fileResult.getId());
        submitLog.setStatus(SubmitStatus.PASS.name());
        submitLog.setUserId(userId);
        try {
            submitLogDbService.save(submitLog);
        } catch (DuplicateKeyException e) {
            // 并发下同一 requestId 已被处理：回滚本事务，回放已有记录
            markRollbackOnly();
            KbSubmitLog raced = submitLogDbService.getByRequestId(request.getRequestId());
            if (ObjectUtil.isNull(raced)) {
                log.warn("===> FileSubmitServiceImpl submit 幂等冲突重查为空, requestId={}（唯一键冲突但未查到已有记录）",
                        request.getRequestId());
                throw e;
            }
            log.info("===> FileSubmitServiceImpl submit 幂等冲突回放, requestId={}", request.getRequestId());
            return inputVoAssembler.toSubmitResponse(raced, findParseTaskId(raced.getFileResultId()));
        }

        log.info("===> FileSubmitServiceImpl submit 建档完成（待手动解析）, kbId={}, fileId={}, fileResultId={}, 建档耗时={}ms",
                kb.getId(), request.getFileId(), fileResult.getId(), System.currentTimeMillis() - buildStart);
        return inputVoAssembler.toSubmitResponse(submitLog, null);
    }

    /**
     * 提交记录分页查询：按知识库分页取 kb_submit_log 并转换为 VO（PASS/FAIL 均含，失败原因随行返回）。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @param status         提交结果筛选（PASS/FAIL，可选）
     * @param fileName       文件名模糊筛选（可选）
     * @return 提交记录分页 VO
     */
    @Override
    public IPage<SubmitLogVO> pageSubmitLogs(long current, long size, Long knowledgeBaseId, String status, String fileName) {
        return submitLogDbService.pageByKb(current, size, knowledgeBaseId, status, fileName)
                .convert(inputVoAssembler::toSubmitLogVO);
    }

    /**
     * 文件结果分页查询：分页取 kb_file_result，批量装配来源文件信息与
     * 五环节（PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED）最新任务状态。
     *
     * <p>stage 参数经 {@link StageFunnelRules#resolveUpstreamStage(String)} 做合法性校验（非法值 40001）；
     * 按上游产物过滤在解析环节（产物表）落地后启用。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @param stage          环节（可选）
     * @return 文件结果分页 VO（含来源文件信息与各环节状态列表）
     */
    @Override
    public IPage<FileResultVO> pageFileResults(long current, long size, Long knowledgeBaseId, String stage) {
        StageFunnelRules.resolveUpstreamStage(stage);
        IPage<KbFileResult> page = fileResultDbService.pageByKb(current, size, knowledgeBaseId);
        List<Long> sourceIds = page.getRecords().stream()
                .map(KbFileResult::getSourceFileId)
                .distinct()
                .toList();
        Map<Long, KbSourceFile> sourceMap = sourceIds.isEmpty() ? Map.of()
                : sourceFileDbService.listByIds(sourceIds).stream()
                        .collect(Collectors.toMap(KbSourceFile::getId, Function.identity()));
        IPage<FileResultVO> voPage = page.convert(result -> inputVoAssembler.toFileResultVO(result, sourceMap.get(result.getSourceFileId())));

        // 环节状态：列表行附各环节最新任务状态（PARSE→STRUCTURE→PREPROCESS→CHUNK→EMBED 五环节）
        List<Long> resultIds = voPage.getRecords().stream().map(FileResultVO::getId).toList();
        // ① 每环节一次 in 查询（id 倒序；按 fileResultId 去重取首条即最新任务）
        List<String> statusStages = List.of(PipelineStage.PARSE.name(), PipelineStage.STRUCTURE.name(),
                PipelineStage.PREPROCESS.name(), PipelineStage.CHUNK.name(), PipelineStage.EMBED.name());
        Map<String, Map<Long, KbPipelineTask>> stageTaskMaps = new HashMap<>();
        for (String statusStage : statusStages) {
            Map<Long, KbPipelineTask> byFileResult = resultIds.isEmpty() ? Map.of()
                    : pipelineTaskDbService.listByFileResultIdsAndStage(resultIds, statusStage).stream()
                            .collect(Collectors.toMap(KbPipelineTask::getFileResultId, Function.identity(),
                                    (first, second) -> first));
            stageTaskMaps.put(statusStage, byFileResult);
        }
        // ② 按环节顺序装配状态列表（无任务的环节不出现在列表中）
        voPage.getRecords().forEach(vo -> {
            List<StageStatusVO> statuses = new ArrayList<>();
            for (String statusStage : statusStages) {
                KbPipelineTask task = stageTaskMaps.get(statusStage).get(vo.getId());
                if (ObjectUtil.isNotNull(task)) {
                    statuses.add(taskVoAssembler.toStageStatusVO(task));
                }
            }
            if (!statuses.isEmpty()) {
                vo.setStageStatuses(statuses);
            }
        });
        return voPage;
    }

    /** 按 fileId 复用来源文件；并发撞 uk_file_id 时重查复用（同文件不重复建档）。 */
    private KbSourceFile findOrCreateSourceFile(FileSubmitRequest request, FileValidationResult validationResult,
                                                FileMetadata metadata, Long userId) {
        KbSourceFile sourceFile = sourceFileDbService.findByFileId(request.getFileId());
        if (ObjectUtil.isNotNull(sourceFile)) {
            log.info("===> FileSubmitServiceImpl submit 复用来源文件, fileId={}, sourceFileId={}",
                    request.getFileId(), sourceFile.getId());
            return sourceFile;
        }
        sourceFile = new KbSourceFile();
        sourceFile.setFileId(request.getFileId());
        sourceFile.setSha256(validationResult.getSha256());
        sourceFile.setFileName(metadata.getFileName());
        sourceFile.setMimeType(validationResult.getMimeType());
        sourceFile.setFileSize(validationResult.getSize());
        sourceFile.setInputSnapshot(JsonUtil.toJsonStr(metadata));
        sourceFile.setUserId(userId);
        try {
            sourceFileDbService.save(sourceFile);
        } catch (DuplicateKeyException e) {
            KbSourceFile raced = sourceFileDbService.findByFileId(request.getFileId());
            if (ObjectUtil.isNull(raced)) {
                throw e;
            }
            log.info("===> FileSubmitServiceImpl submit 并发复用来源文件, fileId={}, sourceFileId={}",
                    request.getFileId(), raced.getId());
            return raced;
        }
        return sourceFile;
    }

    /** 校验失败：只记 FAIL 日志一条，不建结果；文件名尽力取，取不到存空串（列 NOT NULL）。 */
    private FileSubmitResponse recordFailAndReturn(Long knowledgeBaseId, FileSubmitRequest request, String failReason) {
        String fileName = "";
        try {
            FileMetadata metadata = fileStorage.metadata(request.getFileId());
            fileName = StrUtil.blankToDefault(metadata.getFileName(), "");
        } catch (Exception e) {
            // 文件不存在等场景元数据不可得，fileName 留空
            log.debug("元数据不可得, 文件名留空, fileId={}", request.getFileId());
        }
        KbSubmitLog failLog = new KbSubmitLog();
        failLog.setKnowledgeBaseId(knowledgeBaseId);
        failLog.setRequestId(request.getRequestId());
        failLog.setFileId(request.getFileId());
        failLog.setFileName(fileName);
        // sha256 列 NOT NULL 无默认值；校验失败（尤其文件不存在）算不出指纹，存空串占位
        failLog.setSha256("");
        failLog.setStatus(SubmitStatus.FAIL.name());
        failLog.setFailReason(failReason);
        failLog.setUserId(currentUserId());
        submitLogDbService.save(failLog);
        log.info("===> FileSubmitServiceImpl submit 校验失败, kbId={}, fileId={}, fileName={}, failReason={}",
                knowledgeBaseId, request.getFileId(), fileName, failReason);
        return inputVoAssembler.toSubmitResponse(failLog, null);
    }

    /** 当前登录用户 ID（无登录态返回 null）。 */
    private Long currentUserId() {
        KnowledgeUser user = SecurityUtils.getUser();
        return user == null ? null : user.getId();
    }

    /** 事务内捕获异常后返回：显式标记回滚，避免孤儿三写提交。 */
    private void markRollbackOnly() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /** 查文件结果已有的 PARSE 任务 ID（幂等回放响应用；fileResultId 为空或尚无任务返回 null）。 */
    private Long findParseTaskId(Long fileResultId) {
        if (ObjectUtil.isNull(fileResultId)) {
            return null;
        }
        KbPipelineTask task = pipelineTaskDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.PARSE.name());
        return ObjectUtil.isNull(task) ? null : task.getId();
    }

}
