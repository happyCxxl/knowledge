package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.auth.db.UserDbService;
import com.knowledge.biz.service.HomeService;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbIndexSetDbService;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbSubmitLogDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KbAuditLog;
import com.knowledge.common.domain.entity.KbIndexSet;
import com.knowledge.common.domain.entity.KbIndexVersion;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbSubmitLog;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.entity.User;
import com.knowledge.common.dto.request.home.HomeActivityQueryDto;
import com.knowledge.common.dto.request.home.HomeRecentSubmitQueryDto;
import com.knowledge.common.dto.response.home.HomeActivityVO;
import com.knowledge.common.dto.response.home.HomeRecentSubmitVO;
import com.knowledge.common.dto.response.home.HomeSummaryVO;
import com.knowledge.common.enums.input.FileValidationFailLabels;
import com.knowledge.common.enums.knowledge.AuditActionLabels;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.enums.knowledge.AuditObjectType;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.enums.knowledge.StrategyBindingSwitch;
import com.knowledge.common.enums.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页服务实现：资产速览 + 行为记录。
 *
 * <p>两点实现口径：
 *
 * <ol>
 *   <li>**对象名反查按类型分派**（审计表只存 object_id）。
 *       索引版本特殊：审计只记版本号，这里回填成「{知识库名} {版本号}」，
 *       否则「发布索引 v3」看不出是哪个库的；</li>
 *   <li>**批量反查、不逐行查库**：先按类型收集一页里的 ID，各查一次批量取回，
 *       再装配 VO —— 一页 20 条最多 4 次附加查询，与页大小无关。</li>
 * </ol>
 *
 * <p>所有码值都走枚举（{@link AuditActionType} / {@link AuditObjectType} /
 * {@link StrategyType}），本类不再持有字符串常量与映射表。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final KnowledgeBaseDbService knowledgeBaseDbService;
    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final KbIndexVersionDbService indexVersionDbService;
    private final KbIndexSetDbService indexSetDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbAuditLogDbService auditLogDbService;
    private final KbSubmitLogDbService submitLogDbService;
    private final UserDbService userDbService;

    @Override
    public HomeSummaryVO summary() {
        HomeSummaryVO vo = new HomeSummaryVO();
        vo.setKnowledgeBaseCount(knowledgeBaseDbService.countByStatus(null));
        vo.setEnabledKnowledgeBaseCount(
                knowledgeBaseDbService.countByStatus(KnowledgeBaseStatus.ACTIVE.getCode()));

        // 策略版本按四类分别计数（含已停用：首页看的是"有多少资产"）
        long preprocess = countStrategy(StrategyType.PREPROCESS);
        long chunk = countStrategy(StrategyType.CHUNK);
        long embed = countStrategy(StrategyType.EMBED);
        long retrieval = countStrategy(StrategyType.RETRIEVAL);
        vo.setPreprocessVersionCount(preprocess);
        vo.setChunkVersionCount(chunk);
        vo.setEmbedVersionCount(embed);
        vo.setRetrievalVersionCount(retrieval);
        vo.setStrategyVersionCount(preprocess + chunk + embed + retrieval);

        vo.setIndexVersionCount(indexVersionDbService.count());
        // 在线数：正常只有 0 或 1 个（单指针），这里按"索引集合里已设发布指针的"计数
        vo.setOnlineIndexVersionCount(indexSetDbService.lambdaQuery()
                .isNotNull(KbIndexSet::getCurrentPublishedVersionId).count());

        vo.setDocumentCount(fileResultDbService.countAll());
        return vo;
    }

    private long countStrategy(StrategyType type) {
        return strategyVersionDbService.lambdaQuery()
                .eq(KbPipelineStrategyVersion::getType, type.key()).count();
    }

    @Override
    public IPage<HomeRecentSubmitVO> recentSubmits(HomeRecentSubmitQueryDto query) {
        long current = query.currentOrDefault();
        long size = query.sizeOrDefault();
        IPage<KbSubmitLog> page = submitLogDbService.pageRecent(current, size);
        List<KbSubmitLog> rows = page.getRecords();
        Page<HomeRecentSubmitVO> result = new Page<>(current, size, page.getTotal());
        if (rows.isEmpty()) {
            return result;
        }

        Map<Long, KnowledgeBase> kbById = loadSubmitsKnowledgeBases(rows);
        Map<Long, User> userById = loadSubmitsUsers(rows);
        List<HomeRecentSubmitVO> vos = new ArrayList<>(rows.size());
        for (KbSubmitLog row : rows) {
            vos.add(toRecentSubmitVO(row, kbById, userById));
        }
        result.setRecords(vos);
        return result;
    }

    /** 提交行 → VO：补上知识库名与提交人，失败原因翻译成中文 */
    private HomeRecentSubmitVO toRecentSubmitVO(KbSubmitLog row, Map<Long, KnowledgeBase> kbById,
                                               Map<Long, User> userById) {
        HomeRecentSubmitVO vo = new HomeRecentSubmitVO();
        vo.setId(row.getId());
        vo.setFileName(row.getFileName());
        vo.setKnowledgeBaseId(row.getKnowledgeBaseId());
        KnowledgeBase kb = ObjectUtil.isNull(row.getKnowledgeBaseId())
                ? null : kbById.get(row.getKnowledgeBaseId());
        vo.setKnowledgeBaseName(ObjectUtil.isNotNull(kb)
                ? kb.getName() : "知识库 " + row.getKnowledgeBaseId());
        vo.setOperator(resolveSubmitOperator(row, userById));
        vo.setStatus(row.getStatus());
        vo.setFailReason(row.getFailReason());
        vo.setFailReasonLabel(FileValidationFailLabels.label(row.getFailReason()));
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /**
     * 提交人：优先按 `user_id` 反查用户名（提交日志里可能存了已改名/已删的账号）；
     * 查不到再回落 `create_by`（审计监听器确保有值，无认证上下文时为 system）。
     */
    private String resolveSubmitOperator(KbSubmitLog row, Map<Long, User> userById) {
        User user = ObjectUtil.isNull(row.getUserId()) ? null : userById.get(row.getUserId());
        if (ObjectUtil.isNotNull(user) && StrUtil.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        return row.getCreateBy();
    }

    /** 收集本页提交记录涉及的知识库 ID → 实体 */
    private Map<Long, KnowledgeBase> loadSubmitsKnowledgeBases(List<KbSubmitLog> rows) {
        Set<Long> ids = new HashSet<>();
        for (KbSubmitLog row : rows) {
            if (ObjectUtil.isNotNull(row.getKnowledgeBaseId())) {
                ids.add(row.getKnowledgeBaseId());
            }
        }
        return loadKnowledgeBasesByIds(ids);
    }

    /** 收集本页提交记录涉及的用户 ID → 实体 */
    private Map<Long, User> loadSubmitsUsers(List<KbSubmitLog> rows) {
        Set<Long> ids = new HashSet<>();
        for (KbSubmitLog row : rows) {
            if (ObjectUtil.isNotNull(row.getUserId())) {
                ids.add(row.getUserId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : userDbService.listByIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    @Override
    public IPage<HomeActivityVO> activities(HomeActivityQueryDto query) {
        long current = query.currentOrDefault();
        long size = query.sizeOrDefault();
        IPage<KbAuditLog> page = auditLogDbService.pageActivities(current, size, query.getActionType(),
                query.getOperator(), query.getBeginTime(), query.getEndTime());
        List<KbAuditLog> rows = page.getRecords();
        Page<HomeActivityVO> result = new Page<>(current, size, page.getTotal());
        if (rows.isEmpty()) {
            return result;
        }

        Map<Long, KnowledgeBase> kbById = loadKnowledgeBases(rows);
        Map<Long, User> userById = loadUsers(rows);
        Map<Long, String> versionLabelById = loadIndexVersionLabels(rows);

        List<HomeActivityVO> vos = new ArrayList<>(rows.size());
        for (KbAuditLog row : rows) {
            vos.add(toActivityVO(row, kbById, userById, versionLabelById));
        }
        result.setRecords(vos);
        return result;
    }

    /** 审计行 → VO：动作与对象都翻译成可读文本 */
    private HomeActivityVO toActivityVO(KbAuditLog row, Map<Long, KnowledgeBase> kbById,
                                       Map<Long, User> userById, Map<Long, String> versionLabelById) {
        HomeActivityVO vo = new HomeActivityVO();
        vo.setId(row.getId());
        vo.setActionType(row.getActionType());
        vo.setActionLabel(AuditActionLabels.label(row.getActionType(), row.getAfterSummary()));
        vo.setObjectType(row.getObjectType());
        AuditObjectType objectType = AuditObjectType.of(row.getObjectType());
        vo.setObjectTypeLabel(objectType == null ? row.getObjectType() : objectType.label());
        vo.setObjectId(row.getObjectId());
        String objectName = resolveObjectName(row, kbById, userById, versionLabelById);
        vo.setObjectName(objectName);
        vo.setBeforeSummary(normalizeSummary(row, row.getBeforeSummary(), objectName, true));
        vo.setAfterSummary(normalizeSummary(row, row.getAfterSummary(), objectName, false));
        vo.setOperator(row.getCreateBy());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /**
     * 把审计摘要归一化成可读文本。
     *
     * <p>审计表里的摘要**格式不统一**：索引类动作存的是版本号（`v2`）、
     * 策略绑定存的是 `CHUNK=embed-default-v1`、而知识库类动作存的是**整个对象的 JSON**
     * （写入侧如此，见 `KnowledgeBaseServiceImpl`）。这里按格式识别：
     *
     * <ul>
     *   <li>JSON → **反序列化成实体**再取字段，不按字符串键名取值（键名写错编译期发现不了）；</li>
     *   <li>纯文本 → 原样返回。</li>
     * </ul>
     */
    private String normalizeSummary(KbAuditLog row, String raw, String objectName, boolean isBefore) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        KnowledgeBase snapshot = parseSnapshot(row, trimmed);
        if (snapshot == null) {
            // 不是合法快照就原样返回，不吞掉内容
            return trimmed;
        }
        // CREATE 没有「变更前」这个概念：跳过，否则会与 after 重复
        if (isBefore && AuditActionType.CREATE.name().equals(row.getActionType())) {
            return null;
        }
        // 其余动作的 before 是变更前快照：给出名称即可，整份快照对读日志的人没有价值
        if (isBefore) {
            return snapshot.getName();
        }
        return describeSnapshot(snapshot, objectName);
    }

    /**
     * 审计摘要 JSON → 知识库快照。
     *
     * <p>用强类型接收而不是按字符串键名取值：键名与实体字段的对应关系由 Jackson 保证，
     * 字段改名时编译期就能发现（字符串键名只能运行期踩）。
     *
     * @return 解析成功返回实体；非法 JSON 返回 null（调用方原样返回原文）
     */
    private KnowledgeBase parseSnapshot(KbAuditLog row, String json) {
        try {
            return JSONUtil.toBean(json, KnowledgeBase.class);
        } catch (Exception e) {
            log.warn("审计摘要不是合法的知识库快照，原样返回, id={}", row.getId());
            return null;
        }
    }

    /** 对象快照 → 一行可读描述；抽不出任何字段时回落成对象名 */
    private String describeSnapshot(KnowledgeBase snapshot, String objectName) {
        List<String> parts = new ArrayList<>(4);
        addIfPresent(parts, "名称", snapshot.getName());
        addIfPresent(parts, "说明", snapshot.getDescription());
        if (ObjectUtil.isNotNull(snapshot.getStrategyBindingEnabled())) {
            parts.add("策略绑定 " + (StrategyBindingSwitch.isOn(snapshot.getStrategyBindingEnabled()) ? "开启" : "关闭"));
        }
        KnowledgeBaseStatus status = KnowledgeBaseStatus.of(snapshot.getStatus());
        if (ObjectUtil.isNotNull(status)) {
            parts.add(status == KnowledgeBaseStatus.ACTIVE ? "启用" : "停用");
        }
        if (parts.isEmpty()) {
            return objectName;
        }
        return String.join(" · ", parts);
    }

    private void addIfPresent(List<String> parts, String label, String value) {
        if (StrUtil.isNotBlank(value)) {
            parts.add(label + " " + value);
        }
    }

    /**
     * 按对象类型反查名字。
     *
     * <p>知识库被逻辑删除后就查不到了，此时从审计摘要的 JSON 里捞名称兜底
     * （`DELETE` 动作的 before 里带着删除前的对象快照）；
     * 都取不到才回落成「{类型} {ID}」，至少还能看出是哪类对象的哪一条。
     */
    private String resolveObjectName(KbAuditLog row, Map<Long, KnowledgeBase> kbById,
                                     Map<Long, User> userById, Map<Long, String> versionLabelById) {
        Long objectId = parseId(row.getObjectId());
        if (ObjectUtil.isNull(objectId)) {
            return row.getObjectId();
        }
        AuditObjectType type = AuditObjectType.of(row.getObjectType());
        if (type == AuditObjectType.KNOWLEDGE_BASE) {
            KnowledgeBase kb = kbById.get(objectId);
            if (ObjectUtil.isNotNull(kb)) {
                return kb.getName();
            }
            // 已删除/已清理：从摘要快照里捞名称
            String fromSummary = nameFromSummary(row);
            return StrUtil.blankToDefault(fromSummary, "知识库 " + objectId);
        }
        if (type == AuditObjectType.INDEX_VERSION) {
            // 索引版本：审计只记版本号，回填成「{知识库名} {版本号}」才有意义
            return versionLabelById.getOrDefault(objectId, "索引版本 " + objectId);
        }
        if (type == AuditObjectType.KB_USER) {
            User user = userById.get(objectId);
            return ObjectUtil.isNotNull(user) ? user.getUsername() : "账号 " + objectId;
        }
        return row.getObjectType() + " " + objectId;
    }

    /**
     * 从审计摘要里取对象名（知识库被删后表里查不到，只能从 JSON 快照捞）。
     *
     * <p>变更后优先：CREATE/UPDATE 的 after 是最新快照；DELETE 只有 before。
     *
     * @return 快照里的名称；不是 JSON 快照或没有名称时返回 null
     */
    private String nameFromSummary(KbAuditLog row) {
        String raw = StrUtil.isNotBlank(row.getAfterSummary()) ? row.getAfterSummary() : row.getBeforeSummary();
        if (StrUtil.isBlank(raw) || !raw.trim().startsWith("{")) {
            return null;
        }
        KnowledgeBase snapshot = parseSnapshot(row, raw.trim());
        return ObjectUtil.isNull(snapshot) ? null : snapshot.getName();
    }

    /** 收集本页涉及的索引版本 ID → 「{知识库名} {版本号}」 */
    private Map<Long, String> loadIndexVersionLabels(List<KbAuditLog> rows) {
        Set<Long> versionIds = collectIds(rows, AuditObjectType.INDEX_VERSION);
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        List<KbIndexVersion> versions = indexVersionDbService.listByIds(versionIds);
        if (versions.isEmpty()) {
            return Map.of();
        }
        // 版本 → 索引集合 → 知识库名（两次批量查询）
        Set<Long> setIds = new HashSet<>();
        for (KbIndexVersion version : versions) {
            if (ObjectUtil.isNotNull(version.getIndexSetId())) {
                setIds.add(version.getIndexSetId());
            }
        }
        Map<Long, Long> kbIdBySetId = new HashMap<>();
        if (!setIds.isEmpty()) {
            for (KbIndexSet set : indexSetDbService.listByIds(setIds)) {
                kbIdBySetId.put(set.getId(), set.getKnowledgeBaseId());
            }
        }
        Map<Long, KnowledgeBase> kbById = loadKnowledgeBasesByIds(new HashSet<>(kbIdBySetId.values()));

        Map<Long, String> labels = new HashMap<>();
        for (KbIndexVersion version : versions) {
            Long kbId = kbIdBySetId.get(version.getIndexSetId());
            KnowledgeBase kb = ObjectUtil.isNull(kbId) ? null : kbById.get(kbId);
            String kbName = ObjectUtil.isNotNull(kb) ? kb.getName() : "知识库";
            labels.put(version.getId(), kbName + " " + version.getVersionNo());
        }
        return labels;
    }

    /** 收集本页涉及的知识库 ID → 实体 */
    private Map<Long, KnowledgeBase> loadKnowledgeBases(List<KbAuditLog> rows) {
        return loadKnowledgeBasesByIds(collectIds(rows, AuditObjectType.KNOWLEDGE_BASE));
    }

    private Map<Long, KnowledgeBase> loadKnowledgeBasesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, KnowledgeBase> map = new HashMap<>();
        for (KnowledgeBase kb : knowledgeBaseDbService.listByIds(ids)) {
            map.put(kb.getId(), kb);
        }
        return map;
    }

    /** 收集本页涉及的用户 ID → 实体 */
    private Map<Long, User> loadUsers(List<KbAuditLog> rows) {
        Set<Long> ids = collectIds(rows, AuditObjectType.KB_USER);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : userDbService.listByIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    /** 按对象类型收集 object_id */
    private Set<Long> collectIds(List<KbAuditLog> rows, AuditObjectType objectType) {
        Set<Long> ids = new HashSet<>();
        for (KbAuditLog row : rows) {
            if (!objectType.key().equals(row.getObjectType())) {
                continue;
            }
            Long id = parseId(row.getObjectId());
            if (ObjectUtil.isNotNull(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** object_id 是 varchar，历史脏数据可能不是数字；解析失败返回 null 而不是抛错 */
    private Long parseId(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("审计记录 object_id 非数字，跳过反查, objectId={}", raw);
            return null;
        }
    }
}
