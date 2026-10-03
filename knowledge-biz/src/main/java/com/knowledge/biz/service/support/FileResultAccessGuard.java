package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文件结果归属守卫：按 fileResultId 直取的接口在服务入口统一过这一关。
 *
 * <p>文件结果自身没有归属列，归属经 `kb_file_result.knowledge_base_id` 反查知识库：
 * 按 ID 取「当前用户可访问的」知识库（存在性 + 归属，与知识库列表那批接口同一口径）。
 *
 * <p>不可访问与不存在在响应上完全一致（{@link ErrorCode#KB_NOT_FOUND}），
 * 不把别人的 ID 变成可探测的信息。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class FileResultAccessGuard {

    private final KbFileResultDbService fileResultDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;

    /**
     * 取「当前用户可访问的」文件结果：不存在或不可访问一律拒绝。
     *
     * @param fileResultId 文件结果 ID
     * @return 文件结果实体
     * @throws KnowledgeException 无登录上下文（UNAUTHORIZED 40101）或不可访问（KB_NOT_FOUND 40401）
     */
    public KbFileResult require(Long fileResultId) {
        return require(fileResultId, SecurityUtil.getUser());
    }

    /**
     * 按指定用户取「可访问的」文件结果。
     *
     * @param fileResultId 文件结果 ID
     * @param user         登录用户（可空：无登录上下文一律拒绝）
     * @return 文件结果实体
     * @throws KnowledgeException 无登录上下文（UNAUTHORIZED 40101）或不可访问（KB_NOT_FOUND 40401）
     */
    public KbFileResult require(Long fileResultId, KnowledgeUser user) {
        // 先定登录上下文：无登录不进入"按 ID 取数"的路径
        KnowledgeBaseRules.visibleOwnerId(user);
        KbFileResult fileResult = ObjectUtil.isNull(fileResultId) ? null : fileResultDbService.getById(fileResultId);
        check(fileResult, user);
        return fileResult;
    }

    /**
     * 校验「已取到的」文件结果是否可访问（取不到同样拒绝）。
     *
     * @param fileResult 已取到的文件结果，可空
     * @throws KnowledgeException 无登录上下文（UNAUTHORIZED 40101）或不可访问（KB_NOT_FOUND 40401）
     */
    public void check(KbFileResult fileResult) {
        check(fileResult, SecurityUtil.getUser());
    }

    /**
     * 按指定用户校验「已取到的」文件结果。
     *
     * @param fileResult 已取到的文件结果，可空
     * @param user       登录用户（可空：无登录上下文一律拒绝）
     * @throws KnowledgeException 无登录上下文（UNAUTHORIZED 40101）或不可访问（KB_NOT_FOUND 40401）
     */
    public void check(KbFileResult fileResult, KnowledgeUser user) {
        KnowledgeBaseRules.visibleOwnerId(user);
        if (ObjectUtil.isNull(fileResult)) {
            throw new KnowledgeException(ErrorCode.KB_NOT_FOUND);
        }
        // 库不存在/已删除（40401）或非本人可访问（40401）
        KnowledgeBaseRules.checkAccessible(
                knowledgeBaseDbService.getActiveById(fileResult.getKnowledgeBaseId()), user);
    }
}
