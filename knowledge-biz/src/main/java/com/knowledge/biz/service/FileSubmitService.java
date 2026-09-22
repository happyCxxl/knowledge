package com.knowledge.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.dto.request.input.FileSubmitRequest;
import com.knowledge.common.dto.response.input.FileResultVO;
import com.knowledge.common.dto.response.input.FileSubmitResponse;
import com.knowledge.common.dto.response.input.SubmitLogVO;

/**
 * 文件提交编排服务（文档输入环节）。
 *
 * <p>关键语义（实现见 {@code FileSubmitServiceImpl}）：
 * <ul>
 *   <li>幂等：以 requestId 为幂等键，重复请求返回首次提交记录，不重复建档；</li>
 *   <li>失败分级：知识库归属失败抛业务异常不记日志；
 *       文件校验不通过记 FAIL 提交日志并正常返回；</li>
 *   <li>提交成功只建档、不触发解析——后续环节由页面在文件结果页手动触发（手动逐环节口径）。</li>
 * </ul>
 *
 * @author cxxl
 */
public interface FileSubmitService {

    /**
     * 提交文件：幂等检查 → 归属校验 → 文件校验 → 建档三写
     * （kb_source_file / kb_file_result / kb_submit_log，同一事务）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request         提交请求（fileId + requestId）
     * @return 提交响应：新建提交 pipelineTaskId 为 null；
     *         幂等回放时回填该文件结果已有的解析任务 ID（可能为 null）
     * @apiNote 失败语义：知识库不存在/未启用抛 KnowledgeException（40401/40421）；
     *          幂等键缺失抛 KnowledgeException（40420）；
     *          文件校验不通过（4041x，含文件不存在）不抛异常，记 FAIL 提交日志并正常返回。
     */
    FileSubmitResponse submit(Long knowledgeBaseId, FileSubmitRequest request);

    /**
     * 提交记录分页查询：数据源 kb_submit_log（PASS/FAIL 均记录），
     * 支持按提交结果与文件名模糊筛选。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @param status         提交结果筛选（PASS/FAIL，可选）
     * @param fileName       文件名模糊筛选（可选）
     * @return 提交记录分页 VO（含失败原因）
     */
    IPage<SubmitLogVO> pageSubmitLogs(long current, long size, Long knowledgeBaseId, String status, String fileName);

    /**
     * 文件结果分页查询（执行链工作台列表数据源）：每行附带各环节最新任务状态。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @param stage          环节（可选；合法值 PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED，非法值 40001）
     * @return 文件结果分页 VO（含来源文件信息与各环节状态列表）
     */
    IPage<FileResultVO> pageFileResults(long current, long size, Long knowledgeBaseId, String stage);
}
