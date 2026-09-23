package com.knowledge.biz.service;

import com.knowledge.common.dto.response.stagecontent.StageContentVO;

/**
 * 产物内容查询（step-12 B2，恢复 T3）：对比视图内容层数据源，纯读。
 *
 * @author cxxl
 */
public interface StageContentQueryService {

    /**
     * 环节产物内容：白名单 PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED（非法 40001）；
     * 按 task.productId 精确取该次运行产物，历史任务同样可展示（latest=该次运行产物是否可用）。
     *
     * @param fileResultId 文件结果 ID（不存在 40432）
     * @param stage        环节（必填，白名单校验）
     * @param taskId       可选：缺省取最新任务；不属于该文件该环节 40001
     * @return 产物内容
     */
    StageContentVO stageContent(Long fileResultId, String stage, Long taskId);
}
