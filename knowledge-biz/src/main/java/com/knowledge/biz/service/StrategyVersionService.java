package com.knowledge.biz.service;

import com.knowledge.common.dto.request.strategy.StrategyVersionCreateDto;
import com.knowledge.common.dto.request.strategy.StrategyVersionUpdateDto;
import com.knowledge.common.dto.response.strategy.StrategyVersionVO;

import java.util.List;
import java.util.Set;

/**
 * 策略版本服务：切片策略的下拉查询与管理（预处理/向量化等类型随对应阶段加入）。
 * 口径：策略版本行不可变——编辑 = 复制新行；停用代替删除（有绑定引用禁物理删除）。
 *
 * @author cxxl
 */
public interface StrategyVersionService {

    /** 已支持管理/列表/注册的策略类型（其余类型随环节实现加入） */
    Set<String> SUPPORTED_TYPES = Set.of("CHUNK");

    /** 知识库可绑定类型（其余类型随环节实现加入） */
    Set<String> BINDABLE_TYPES = Set.of("CHUNK");

    /**
     * 列表：includeInactive=false 只启用中（触发前下拉用），true 全部（管理用）；空/非法类型 40001。
     */
    List<StrategyVersionVO> list(String type, boolean includeInactive);

    /**
     * 创建（默认 ACTIVE；type 白名单校验；同环节同名版本撞唯一键 → 40001）。
     */
    StrategyVersionVO create(StrategyVersionCreateDto dto);

    /**
     * 编辑 = 复制新版本（策略版本行不可变）：type 归属旧行不变；新行按 dto 的 name/version/configSnapshot
     * 注册（撞唯一键 40001），旧行原样保留；不存在 40433。
     */
    StrategyVersionVO update(Long id, StrategyVersionUpdateDto dto);

    /**
     * 启用/停用（幂等成功；不存在 40433）。
     */
    StrategyVersionVO enable(Long id);

    StrategyVersionVO disable(Long id);

    /**
     * 物理删除（被知识库绑定引用 → 40452 拒绝，停用代替删除；无引用可删；不存在 40433）。
     */
    boolean delete(Long id);
}
